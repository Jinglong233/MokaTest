package com.mokatest.platform.demos.qa.ai.context;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.context.ContextBlocks;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.qa.ai.util.RichTextCleaner;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * L1 核心需求上下文：当前需求完整信息（不可裁剪）
 */
@Component
public class RequirementCoreSource {

    @Autowired
    private RequirementMapper requirementMapper;

    public ContextBlocks.Block load(Long entityId, Integer projectId) {
        Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", entityId)
                .eq("project_id", projectId));
        if (req == null) {
            throw new BusinessException("需求不存在");
        }
        return buildBlock(req);
    }

    /** L1 重载：已加载的 Requirement 直接构建 Block，跳过 DB 查询 */
    public ContextBlocks.Block load(Requirement req) {
        return buildBlock(req);
    }

    private ContextBlocks.Block buildBlock(Requirement req) {
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
        return new ContextBlocks.Block("L1", "核心需求", sb.toString(), false);
    }
}
