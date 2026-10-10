package com.mokatest.platform.demos.qa.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 需求工具：供 AI Agent 通过 @Tool 函数调用查询需求。
 * 放在 platform-qa 模块（有 RequirementMapper 访问权）。
 */
@Component
public class RequirementTools {

    @Autowired
    private RequirementMapper requirementMapper;

    @Tool("搜索项目需求，返回匹配的需求列表")
    public List<Map<String, Object>> searchRequirements(@P("搜索关键词") String keyword) {
        Integer projectId = ProjectContextHolder.getProjectId();
        if (projectId == null) return List.of();
        List<Requirement> reqs = requirementMapper.selectList(new QueryWrapper<Requirement>()
                .eq("project_id", projectId)
                .like("title", keyword)
                .select("id", "title", "status", "priority")
                .last("limit 10"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Requirement req : reqs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", req.getId());
            item.put("title", req.getTitle());
            item.put("status", req.getStatus());
            item.put("priority", req.getPriority());
            result.add(item);
        }
        return result;
    }
}
