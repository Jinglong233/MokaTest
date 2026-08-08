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
 * L2 血缘上下文：当前需求的父需求与子需求（不可裁剪）
 */
@Component
public class RelatedRequirementSource implements ContextSource {

    public static final String CODE = "related_requirement";

    @Autowired
    private RequirementMapper requirementMapper;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String level() {
        return "L2";
    }

    @Override
    public ContextBlock load(SkillRequest request) {
        Requirement current = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", request.getEntityId())
                .eq("project_id", request.getProjectId())
                .select("id", "parent_id", "title", "req_code"));
        if (current == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        // 父需求
        if (current.getParentId() != null && current.getParentId() != 0) {
            Requirement parent = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                    .eq("id", current.getParentId())
                    .eq("project_id", request.getProjectId())
                    .select("id", "title", "req_code", "description"));
            if (parent != null) {
                sb.append("父需求：").append(parent.getReqCode()).append(' ').append(parent.getTitle()).append('\n');
            }
        }
        // 子需求
        List<Requirement> children = requirementMapper.selectList(new QueryWrapper<Requirement>()
                .eq("parent_id", current.getId())
                .eq("project_id", request.getProjectId())
                .select("id", "title", "req_code")
                .last("limit 10"));
        if (!children.isEmpty()) {
            sb.append("子需求：\n");
            for (Requirement child : children) {
                sb.append("- ").append(child.getReqCode()).append(' ').append(child.getTitle()).append('\n');
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        ContextBlock block = new ContextBlock("L2", CODE, "关联需求（血缘）", sb.toString());
        block.setTrimmable(false);
        return block;
    }
}
