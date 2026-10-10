package com.mokatest.platform.demos.qa.ai.agent;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 工具：生成测试用例草稿（不入库，需用户手动确认后到用例列表创建）
 */
@Component
public class CreateTestCaseTool implements AgentTool {

    @Override public String name() { return "create_test_case"; }
    @Override public String description() { return "根据需求生成一条测试用例草稿，返回用例名称、类型、优先级、步骤等供用户确认后手动创建"; }
    @Override public String paramSchema() { return "{\"caseName\": \"用例名称\", \"caseType\": \"FUNCTION/API/PERFORMANCE/COMPATIBILITY/SMOKE\", \"priority\": \"P0/P1/P2\", \"requirementId\": \"关联需求ID\", \"testSteps\": [{\"step\": \"步骤\", \"expected\": \"预期\"}]}"; }
    @Override public String requiredPermission() { return "qa:testcase:view"; }

    @Override
    public String execute(AgentContext ctx, JSONObject args) {
        if (args == null || !args.containsKey("caseName")) return "{\"error\": \"caseName 必填\"}";
        JSONObject draft = new JSONObject();
        draft.put("caseName", args.getString("caseName"));
        draft.put("caseType", args.containsKey("caseType") ? args.getString("caseType") : "FUNCTION");
        draft.put("priority", args.containsKey("priority") ? args.getString("priority") : "P2");
        if (args.containsKey("requirementId")) draft.put("requirementId", args.getInteger("requirementId"));
        if (args.containsKey("preCondition")) draft.put("preCondition", args.getString("preCondition"));
        if (args.containsKey("testSteps")) {
            JSONArray steps = args.getJSONArray("testSteps");
            JSONArray out = new JSONArray();
            for (int i = 0; i < steps.size(); i++) {
                JSONObject s = steps.getJSONObject(i);
                JSONObject step = new JSONObject();
                step.put("step", s.getString("step"));
                step.put("expected", s.getString("expected"));
                out.add(step);
            }
            draft.put("testSteps", out);
        }
        draft.put("notice", "此为草稿，需用户到用例列表手动创建确认后入库");
        return draft.toJSONString();
    }
}
