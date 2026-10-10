package com.mokatest.platform.demos.qa.ai.casegen;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import com.mokatest.platform.demos.qa.service.TestCaseService;
import com.mokatest.platform.demos.ai.service.AiGenerationRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话状态统计块构建器。
 *
 * QA 轮组消息前注入确定性数据块，模型读数回答数量/状态类问题。
 * 统计查询本身失败时降级：返回 null，QA 轮正常走（不阻断问答）。
 */
@Component
public class SessionStatsBuilder {

    private static final Logger log = LoggerFactory.getLogger(SessionStatsBuilder.class);

    @Autowired
    private AiGenerationRecordService recordService;
    @Autowired
    private TestCaseService testCaseService;
    @Autowired
    private RequirementMapper requirementMapper;

    /**
     * 构建会话状态统计块文本，失败时返回 null（降级）。
     *
     * @param recordNo  生成记录号
     * @param projectId 项目ID
     * @param entityId  需求ID（可能为 null）
     * @return 统计块文本，或 null
     */
    public String build(String recordNo, Integer projectId, Long entityId) {
        try {
            return doBuild(recordNo, projectId, entityId);
        } catch (Exception e) {
            log.warn("会话状态统计块构建失败，降级跳过 | recordNo={} err={}", recordNo, e.getMessage());
            return null;
        }
    }

    private String doBuild(String recordNo, Integer projectId, Long entityId) {
        // ── 会话级统计（从轮次数据） ──
        com.mokatest.platform.demos.ai.domain.AiGenerationRecord rec = recordService.getByRecordNo(recordNo, projectId);
        if (rec == null) return null;
        List<Map<String, Object>> rounds = recordService.parseRounds(rec.getOutputSnapshot());
        int totalDrafts = 0;
        int genRounds = 0;
        for (Map<String, Object> round : rounds) {
            String status = String.valueOf(round.get("status"));
            String type = String.valueOf(round.get("type"));
            if ("DONE".equals(status) && "gen".equals(type)) {
                genRounds++;
                Object drafts = round.get("drafts");
                if (drafts instanceof List) totalDrafts += ((List<?>) drafts).size();
            }
        }

        // ── 采纳统计（从采纳明细） ──
        Set<String> adoptedIds = recordService.parseAdoptedDraftIds(recordNo);
        int adopted = adoptedIds != null ? adoptedIds.size() : 0;
        int pending = Math.max(0, totalDrafts - adopted);

        StringBuilder sb = new StringBuilder();
        sb.append("## 会话状态（实时统计数据，回答数量/状态类问题以此为准）\n");
        sb.append(String.format("- 本次会话：累计生成草稿 %d 条（%d 轮），已采纳 %d 条，待采纳 %d 条\n",
                totalDrafts, genRounds, adopted, pending));

        // ── 需求级统计（从 test_case 表） ──
        if (entityId != null) {
            long totalCases = testCaseService.count(new QueryWrapper<TestCase>()
                    .eq("requirement_id", entityId)
                    .eq("project_id", projectId));
            long passCount = testCaseService.count(new QueryWrapper<TestCase>()
                    .eq("requirement_id", entityId).eq("project_id", projectId)
                    .eq("last_result", "PASS"));
            long failCount = testCaseService.count(new QueryWrapper<TestCase>()
                    .eq("requirement_id", entityId).eq("project_id", projectId)
                    .eq("last_result", "FAIL"));
            long notRun = totalCases - passCount - failCount;

            String reqTitle = "";
            Requirement req = requirementMapper.selectById(entityId);
            if (req != null) reqTitle = req.getTitle();

            sb.append(String.format("- 当前需求「%s」：已入库用例 %d 条（通过 %d / 失败 %d / 未执行 %d），其中本会话采纳 %d 条\n",
                    reqTitle, totalCases, passCount, failCount, notRun, adopted));
        }

        return sb.toString();
    }
}
