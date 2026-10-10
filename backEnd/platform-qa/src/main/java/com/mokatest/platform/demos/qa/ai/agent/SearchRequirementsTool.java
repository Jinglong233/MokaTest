package com.mokatest.platform.demos.qa.ai.agent;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具：搜索需求（仅当前项目）
 */
@Component
public class SearchRequirementsTool implements AgentTool {

    @Autowired
    private RequirementMapper requirementMapper;

    @Override
    public String name() { return "search_requirements"; }

    @Override
    public String description() { return "按关键词搜索当前项目的需求列表，返回需求编号、标题、状态、优先级"; }

    @Override
    public String paramSchema() { return "{\"keyword\": \"搜索关键词（可选）\"}"; }

    @Override
    public String requiredPermission() { return "qa:requirement:view"; }

    @Override
    public String execute(AgentContext ctx, JSONObject args) {
        String keyword = args != null && args.containsKey("keyword") ? args.getString("keyword") : null;
        QueryWrapper<Requirement> qw = new QueryWrapper<Requirement>()
                .eq("project_id", ctx.getProjectId())
                .orderByDesc("create_time")
                .last("limit 20");
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("title", keyword).or().like("req_code", keyword));
        }
        List<Requirement> list = requirementMapper.selectList(qw);
        JSONArray result = new JSONArray();
        for (Requirement r : list) {
            JSONObject obj = new JSONObject();
            obj.put("id", r.getId());
            obj.put("reqCode", r.getReqCode());
            obj.put("title", r.getTitle());
            obj.put("status", r.getStatus());
            obj.put("priority", r.getPriority());
            result.add(obj);
        }
        return result.toJSONString();
    }
}
