package com.mokatest.platform.demos.qa.ai.context;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.skill.ContextBlock;
import com.mokatest.platform.demos.ai.skill.ContextSource;
import com.mokatest.platform.demos.ai.skill.SkillRequest;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.qa.ai.util.RichTextCleaner;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * L1 核心需求上下文：当前需求完整信息（不可裁剪）
 *
 * 数据边界：按 id + project_id 联合查询，跨项目 entityId 直接报"需求不存在"。
 */
@Component
public class RequirementCoreSource implements ContextSource {

    public static final String CODE = "requirement_core";

    @Autowired
    private RequirementMapper requirementMapper;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String level() {
        return "L1";
    }

    @Override
    public ContextBlock load(SkillRequest request) {
        Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", request.getEntityId())
                .eq("project_id", request.getProjectId()));
        if (req == null) {
            throw new BusinessException("需求不存在");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("需求编号：").append(req.getReqCode()).append('\n');
        sb.append("标题：").append(req.getTitle()).append('\n');
        if (req.getPriority() != null) {
            sb.append("优先级：").append(req.getPriority()).append('\n');
        }
        if (req.getReqType() != null) {
            sb.append("类型：").append(req.getReqType()).append('\n');
        }
        if (req.getDescription() != null && !req.getDescription().isEmpty()) {
            sb.append("描述：\n").append(RichTextCleaner.toPlainText(req.getDescription()));
        }
        ContextBlock block = new ContextBlock("L1", CODE, "核心需求", sb.toString());
        block.setTrimmable(false);
        return block;
    }
}
