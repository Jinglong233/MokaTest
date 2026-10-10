package com.mokatest.platform.demos.qa.ai.context;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.context.ContextBlocks;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L3 同模块上下文：当前需求同模块的其他需求标题（辅助边界分析，可裁剪）
 */
@Component
public class ModuleRequirementSource {

    @Autowired
    private RequirementMapper requirementMapper;

    public ContextBlocks.Block load(Long entityId, Integer projectId) {
        Requirement current = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", entityId)
                .eq("project_id", projectId)
                .select("id", "module_id"));
        if (current == null || current.getModuleId() == null) {
            return null;
        }
        return load(current, projectId);
    }

    /** L3 重载：复用 L1 已加载的 Requirement（含 moduleId），跳过冗余查询 */
    public ContextBlocks.Block load(Requirement current, Integer projectId) {
        if (current.getModuleId() == null) {
            return null;
        }
        List<Requirement> siblings = requirementMapper.selectList(new QueryWrapper<Requirement>()
                .eq("project_id", projectId)
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
        return new ContextBlocks.Block("L3", "同模块其他需求", sb.toString(), true);
    }
}
