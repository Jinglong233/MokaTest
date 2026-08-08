package com.mokatest.platform.demos.qa.ai.context;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.skill.ContextBlock;
import com.mokatest.platform.demos.ai.skill.ContextSource;
import com.mokatest.platform.demos.ai.skill.SkillRequest;
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
public class HistoryBugSource implements ContextSource {

    public static final String CODE = "history_bugs";

    @Autowired
    private BugMapper bugMapper;

    @Autowired
    private RequirementMapper requirementMapper;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String level() {
        return "L5";
    }

    @Override
    public boolean defaultEnabled() {
        // 前端已移除上下文开关：所有上下文默认强关联当前需求加载，
        // 用户可在指令中明确要求不使用某类上下文（由 prompt 约束模型忽略）
        return true;
    }

    @Override
    public ContextBlock load(SkillRequest request) {
        Requirement current = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", request.getEntityId())
                .eq("project_id", request.getProjectId())
                .select("id", "module_id"));
        if (current == null) {
            return null;
        }
        QueryWrapper<Bug> qw = new QueryWrapper<Bug>()
                .eq("project_id", request.getProjectId())
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
        return new ContextBlock("L5", CODE, "同模块历史BUG", sb.toString());
    }
}
