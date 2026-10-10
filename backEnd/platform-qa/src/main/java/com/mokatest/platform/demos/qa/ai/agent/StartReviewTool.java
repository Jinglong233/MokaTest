package com.mokatest.platform.demos.qa.ai.agent;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.qa.aireview.AiRequirementReview;
import com.mokatest.platform.demos.qa.aireview.AiRequirementReviewMapper;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具：发起需求 AI 评审入口卡片
 * <p>
 * Agent 识别到用户想评审需求时调用此工具，
 * 返回需求摘要 + 最近评审状态供前端展示入口卡片，用户点击后跳转评审页。
 * 工具只做「校验 + 摘要返回」，不启动评审（评审动作发生在评审页）。
 */
@Component
public class StartReviewTool implements AgentTool {

    @Autowired
    private RequirementMapper requirementMapper;
    @Autowired
    private AiRequirementReviewMapper reviewMapper;

    @Override
    public String name() {
        return "start_requirement_review";
    }

    @Override
    public String description() {
        return "当用户想对某个【明确指定】的需求进行 AI 评审时调用，传入需求ID，返回需求摘要和最近评审状态。"
                + "AI 评审会从完整性、可测性、边界、安全等维度分析需求质量并输出评审报告。"
                + "注意：若用户未指明具体需求，不要调用本工具，应先用 search_requirements 列出候选需求请用户选择。";
    }

    @Override
    public String paramSchema() {
        return "{\"requirementId\": \"需求ID（必填）\"}";
    }

    @Override
    public String requiredPermission() {
        return "qa:ai:use";
    }

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

        // 查最近一次评审记录
        AiRequirementReview latestReview = reviewMapper.selectOne(new QueryWrapper<AiRequirementReview>()
                .eq("requirement_id", reqId)
                .eq("project_id", ctx.getProjectId())
                .orderByDesc("create_time").last("limit 1"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "review");
        result.put("requirementId", reqId);
        result.put("reqCode", req.getReqCode());
        result.put("title", req.getTitle());
        if (latestReview != null) {
            result.put("lastScore", latestReview.getOverallScore());
            result.put("lastStatus", latestReview.getStatus());
            result.put("lastTime", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(latestReview.getCreateTime()));
        } else {
            result.put("lastScore", null);
            result.put("lastStatus", null);
            result.put("lastTime", null);
        }
        return JSONObject.toJSON(result).toString();
    }
}
