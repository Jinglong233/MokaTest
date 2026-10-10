package com.mokatest.platform.demos.qa.ai.agent;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.qa.domain.Bug;
import com.mokatest.platform.demos.qa.mapper.BugMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchBugsTool implements AgentTool {

    @Autowired
    private BugMapper bugMapper;

    @Override public String name() { return "search_bugs"; }
    @Override public String description() { return "按关键词搜索当前项目的BUG列表，返回BUG编号、标题、严重程度、状态"; }
    @Override public String paramSchema() { return "{\"keyword\": \"搜索关键词（可选）\"}"; }
    @Override public String requiredPermission() { return "qa:bug:view"; }

    @Override
    public String execute(AgentContext ctx, JSONObject args) {
        String keyword = args != null && args.containsKey("keyword") ? args.getString("keyword") : null;
        QueryWrapper<Bug> qw = new QueryWrapper<Bug>()
                .eq("project_id", ctx.getProjectId())
                .orderByDesc("create_time")
                .last("limit 20");
        if (keyword != null && !keyword.isBlank()) {
            qw.like("title", keyword);
        }
        List<Bug> list = bugMapper.selectList(qw);
        JSONArray result = new JSONArray();
        for (Bug b : list) {
            JSONObject obj = new JSONObject();
            obj.put("id", b.getId());
            obj.put("bugCode", b.getBugCode());
            obj.put("title", b.getTitle());
            obj.put("severity", b.getSeverity());
            obj.put("status", b.getStatus());
            result.add(obj);
        }
        return result.toJSONString();
    }
}
