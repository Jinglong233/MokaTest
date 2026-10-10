package com.mokatest.platform.demos.qa.ai.context;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.context.ContextBlocks;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L2 血缘上下文：当前需求的父需求与子需求（不可裁剪）
 */
@Component
public class RelatedRequirementSource {

    @Autowired
    private RequirementMapper requirementMapper;

    public ContextBlocks.Block load(Long entityId, Integer projectId) {
        Requirement current = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", entityId)
                .eq("project_id", projectId)
                .select("id", "parent_id", "title", "req_code"));
        if (current == null) {
            return null;
        }
        return load(current, projectId);
    }

    /** L2 重载：复用 L1 已加载的 Requirement，跳过冗余查询 */
    public ContextBlocks.Block load(Requirement current, Integer projectId) {
        StringBuilder sb = new StringBuilder();
        if (current.getParentId() != null && current.getParentId() != 0) {
            Requirement parent = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                    .eq("id", current.getParentId())
                    .eq("project_id", projectId)
                    .select("id", "title", "req_code", "description"));
            if (parent != null) {
                sb.append("父需求：").append(parent.getReqCode()).append(' ').append(parent.getTitle()).append('\n');
            }
        }
        List<Requirement> children = requirementMapper.selectList(new QueryWrapper<Requirement>()
                .eq("parent_id", current.getId())
                .eq("project_id", projectId)
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
        return new ContextBlocks.Block("L2", "关联需求（血缘）", sb.toString(), false);
    }
}
