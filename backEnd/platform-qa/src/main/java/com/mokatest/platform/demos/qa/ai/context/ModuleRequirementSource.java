package com.mokatest.platform.demos.qa.ai.context;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.skill.ContextBlock;
import com.mokatest.platform.demos.ai.skill.ContextSource;
import com.mokatest.platform.demos.ai.skill.SkillRequest;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L3 同模块上下文：当前需求同模块的其他需求标题（辅助边界分析，可裁剪）
 */
@Component
public class ModuleRequirementSource implements ContextSource {

    public static final String CODE = "module_requirements";

    @Autowired
    private RequirementMapper requirementMapper;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String level() {
        return "L3";
    }

    @Override
    public ContextBlock load(SkillRequest request) {
        Requirement current = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", request.getEntityId())
                .eq("project_id", request.getProjectId())
                .select("id", "module_id"));
        if (current == null || current.getModuleId() == null) {
            return null;
        }
        List<Requirement> siblings = requirementMapper.selectList(new QueryWrapper<Requirement>()
                .eq("project_id", request.getProjectId())
                .eq("module_id", current.getModuleId())
                .ne("id", current.getId())
                .select("id", "title", "req_code", "priority")
                .orderByDesc("update_time")
                .last("limit 15"));
        if (siblings.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Requirement sibling : siblings) {
            sb.append("- ").append(sibling.getReqCode()).append(' ').append(sibling.getTitle());
            if (sibling.getPriority() != null) {
                sb.append("（").append(sibling.getPriority()).append('）');
            }
            sb.append('\n');
        }
        return new ContextBlock("L3", CODE, "同模块其他需求", sb.toString());
    }
}
