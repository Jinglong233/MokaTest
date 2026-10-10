package com.mokatest.platform.demos.qa.ai.context;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.context.ContextBlocks;
import com.mokatest.platform.demos.qa.domain.Bug;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.mapper.BugMapper;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L5 历史BUG上下文：同模块近期 BUG（从历史缺陷反推易错点，最先被裁剪）
 */
@Component
public class HistoryBugSource {

    @Autowired
    private BugMapper bugMapper;

    @Autowired
    private RequirementMapper requirementMapper;

    public ContextBlocks.Block load(Long entityId, Integer projectId) {
        Requirement current = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", entityId)
                .eq("project_id", projectId)
                .select("id", "module_id"));
        if (current == null) {
            return null;
        }
        return load(current, projectId);
    }

    /** L5 重载：复用 L1 已加载的 Requirement（含 moduleId），跳过冗余查询 */
    public ContextBlocks.Block load(Requirement current, Integer projectId) {
        QueryWrapper<Bug> qw = new QueryWrapper<Bug>()
                .eq("project_id", projectId)
                .select("id", "title", "severity", "status")
                .orderByDesc("create_time")
                .last("limit 15");
        if (current.getModuleId() != null) {
            qw.eq("module_id", current.getModuleId());
        }
        List<Bug> bugs = bugMapper.selectList(qw);
        if (bugs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("该模块历史缺陷（生成用例时请覆盖这些易错点）：\n");
        for (Bug bug : bugs) {
            sb.append("- ").append(bug.getTitle());
            if (bug.getSeverity() != null) {
                sb.append("（").append(bug.getSeverity()).append('）');
            }
            sb.append('\n');
        }
        return new ContextBlocks.Block("L5", "同模块历史BUG", sb.toString(), true);
    }
}
