package com.mokatest.platform.demos.qa.ai.context;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.skill.ContextBlock;
import com.mokatest.platform.demos.ai.skill.ContextSource;
import com.mokatest.platform.demos.ai.skill.SkillRequest;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L4 已有用例上下文：当前需求已关联的用例（避免重复生成，可裁剪）
 */
@Component
public class ExistingCaseSource implements ContextSource {

    public static final String CODE = "existing_cases";

    @Autowired
    private TestCaseMapper testCaseMapper;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String level() {
        return "L4";
    }

    @Override
    public ContextBlock load(SkillRequest request) {
        List<TestCase> cases = testCaseMapper.selectList(new QueryWrapper<TestCase>()
                .eq("project_id", request.getProjectId())
                .eq("requirement_id", request.getEntityId())
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
        return new ContextBlock("L4", CODE, "已有关联用例", sb.toString());
    }
}
