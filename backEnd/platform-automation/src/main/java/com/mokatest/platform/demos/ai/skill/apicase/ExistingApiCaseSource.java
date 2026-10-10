package com.mokatest.platform.demos.ai.skill.apicase;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.context.ContextBlocks;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L4 已有用例上下文：该接口下已生成的用例（防重复，可裁剪）
 */
@Component
public class ExistingApiCaseSource {

    @Autowired
    private ApiRequestMapper apiRequestMapper;

    public ContextBlocks.Block load(Long entityId, Integer projectId) {
        List<ApiRequest> cases = apiRequestMapper.selectList(new QueryWrapper<ApiRequest>()
                .eq("project_id", projectId)
                .eq("source_drat_id", entityId)
                .select("id", "api_name")
                .orderByDesc("create_time")
                .last("limit 20"));
        if (cases.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("该接口已有以下用例，新生成的用例不得重复：\n");
        for (ApiRequest c : cases) {
            sb.append("- ").append(c.getApiName()).append('\n');
        }
        return new ContextBlocks.Block("L4", "已有接口用例", sb.toString(), true);
    }
}
