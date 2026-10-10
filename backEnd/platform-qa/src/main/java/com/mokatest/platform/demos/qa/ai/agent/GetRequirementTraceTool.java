package com.mokatest.platform.demos.qa.ai.agent;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.domain.Bug;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import com.mokatest.platform.demos.qa.mapper.BugMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GetRequirementTraceTool implements AgentTool {

    @Autowired private RequirementMapper requirementMapper;
    @Autowired private TestCaseMapper testCaseMapper;
    @Autowired private BugMapper bugMapper;

    @Override public String name() { return "get_requirement_trace"; }
    @Override public String description() { return "查询需求血缘统计：需求详情 + 关联用例数 + 关联BUG数"; }
    @Override public String paramSchema() { return "{\"requirementId\": \"需求ID（必填）\"}"; }
    @Override public String requiredPermission() { return "qa:requirement:view"; }

    @Override
    public String execute(AgentContext ctx, JSONObject args) {
        if (args == null || !args.containsKey("requirementId")) return "{\"error\": \"requirementId 必填\"}";
        Integer reqId = args.getInteger("requirementId");
        Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", reqId).eq("project_id", ctx.getProjectId())
                .select("id", "req_code", "title", "status", "priority"));
        if (req == null) return "{\"error\": \"需求不存在\"}";
        Long caseCount = testCaseMapper.selectCount(new QueryWrapper<TestCase>()
                .eq("project_id", ctx.getProjectId()).eq("requirement_id", reqId));
        Long bugCount = bugMapper.selectCount(new QueryWrapper<Bug>()
                .eq("project_id", ctx.getProjectId()).eq("requirement_id", reqId));
        JSONObject result = new JSONObject();
        result.put("reqCode", req.getReqCode());
        result.put("title", req.getTitle());
        result.put("status", req.getStatus());
        result.put("priority", req.getPriority());
        result.put("caseCount", caseCount);
        result.put("bugCount", bugCount);
        return result.toJSONString();
    }
}
