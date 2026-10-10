package com.mokatest.platform.demos.qa.ai.agent;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.qa.aireview.AiRequirementReview;
import com.mokatest.platform.demos.qa.aireview.AiRequirementReviewMapper;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：启动用例生成卡片流（意图识别触发）
 *
 * Agent 识别到用户想生成测试用例时调用此工具，
 * 返回需求摘要供前端展示卡片①，前端据此切换到 casegen 模式。
 */
@Component
public class StartCasegenTool implements AgentTool {

    @Autowired
    private RequirementMapper requirementMapper;
    @Autowired
    private TestCaseMapper testCaseMapper;
    @Autowired
    private AiRequirementReviewMapper reviewMapper;

    @Override public String name() { return "start_case_generation"; }
    @Override public String description() { return "当用户想为某个【明确指定】的需求生成测试用例时调用，传入需求ID，返回需求摘要供确认后开始生成。"
            + "注意：一次只能为一个需求生成，禁止为多个需求批量调用本工具；"
            + "若用户未指明具体需求（如只说\"生成用例\"），不要调用本工具，应先用 search_requirements 列出候选需求请用户选择一个。"; }
    @Override public String paramSchema() { return "{\"requirementId\": \"需求ID（必填）\"}"; }
    @Override public String requiredPermission() { return "qa:ai:use"; }

    @Override
    public String execute(AgentContext ctx, JSONObject args) {
        if (args == null || !args.containsKey("requirementId")) {
            return "{\"error\": \"requirementId 必填\"}";
        }
        Long reqId = args.getLong("requirementId");
        Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", reqId).eq("project_id", ctx.getProjectId())
                .select("id", "req_code", "title", "status", "priority"));
        if (req == null) return "{\"error\": \"需求不存在\"}";
        Long caseCount = testCaseMapper.selectCount(new QueryWrapper<TestCase>()
                .eq("project_id", ctx.getProjectId()).eq("requirement_id", reqId));
        AiRequirementReview latestReview = reviewMapper.selectOne(new QueryWrapper<AiRequirementReview>()
                .eq("requirement_id", reqId)
                .eq("project_id", ctx.getProjectId())
                .orderByDesc("create_time").last("limit 1"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "casegen");
        result.put("requirementId", reqId);
        result.put("id", req.getId());
        result.put("reqCode", req.getReqCode());
        result.put("title", req.getTitle());
        result.put("status", req.getStatus());
        result.put("priority", req.getPriority());
        result.put("caseCount", caseCount);
        result.put("aiReviewScore", latestReview != null ? latestReview.getOverallScore() : null);
        return JSONObject.toJSON(result).toString();
    }
}
