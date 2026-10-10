package com.mokatest.platform.demos.qa.ai.agent;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchCasesTool implements AgentTool {

    @Autowired
    private TestCaseMapper testCaseMapper;

    @Override public String name() { return "search_cases"; }
    @Override public String description() { return "按关键词搜索当前项目的用例列表，返回用例编号、名称、类型、优先级"; }
    @Override public String paramSchema() { return "{\"keyword\": \"搜索关键词（可选）\"}"; }
    @Override public String requiredPermission() { return "qa:testcase:view"; }

    @Override
    public String execute(AgentContext ctx, JSONObject args) {
        String keyword = args != null && args.containsKey("keyword") ? args.getString("keyword") : null;
        QueryWrapper<TestCase> qw = new QueryWrapper<TestCase>()
                .eq("project_id", ctx.getProjectId())
                .orderByDesc("create_time")
                .last("limit 20");
        if (keyword != null && !keyword.isBlank()) {
            qw.like("case_name", keyword);
        }
        List<TestCase> list = testCaseMapper.selectList(qw);
        JSONArray result = new JSONArray();
        for (TestCase tc : list) {
            JSONObject obj = new JSONObject();
            obj.put("id", tc.getId());
            obj.put("caseCode", tc.getCaseCode());
            obj.put("caseName", tc.getCaseName());
            obj.put("caseType", tc.getCaseType());
            obj.put("priority", tc.getPriority());
            result.add(obj);
        }
        return result.toJSONString();
    }
}
