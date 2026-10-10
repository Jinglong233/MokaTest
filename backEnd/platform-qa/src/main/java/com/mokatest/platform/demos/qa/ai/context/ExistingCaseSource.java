package com.mokatest.platform.demos.qa.ai.context;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.context.ContextBlocks;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L4 已有用例上下文：当前需求已关联的用例（避免重复生成，可裁剪）
 */
@Component
public class ExistingCaseSource {

    @Autowired
    private TestCaseMapper testCaseMapper;

    public ContextBlocks.Block load(Long entityId, Integer projectId) {
        List<TestCase> cases = testCaseMapper.selectList(new QueryWrapper<TestCase>()
                .eq("project_id", projectId)
                .eq("requirement_id", entityId)
                .select("id", "case_name", "case_type", "priority")
                .orderByDesc("update_time")
                .last("limit 20"));
        if (cases.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("以下用例已存在，新生成的用例不得与其重复：\n");
        for (TestCase c : cases) {
            sb.append("- ").append(c.getCaseName());
            if (c.getCaseType() != null) {
                sb.append("（").append(c.getCaseType()).append('）');
            }
            sb.append('\n');
        }
        return new ContextBlocks.Block("L4", "已有关联用例", sb.toString(), true);
    }
}
