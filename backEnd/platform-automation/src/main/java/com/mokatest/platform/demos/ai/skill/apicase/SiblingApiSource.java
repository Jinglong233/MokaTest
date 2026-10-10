package com.mokatest.platform.demos.ai.skill.apicase;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.context.ContextBlocks;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiNodeType;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L3 同目录接口上下文：同父目录的其他接口（辅助理解业务语义，可裁剪）
 */
@Component
public class SiblingApiSource {

    @Autowired
    private ApiRequestMapper apiRequestMapper;

    public ContextBlocks.Block load(Long entityId, Integer projectId) {
        ApiRequest current = apiRequestMapper.selectOne(new QueryWrapper<ApiRequest>()
                .eq("id", entityId)
                .eq("project_id", projectId)
                .select("id", "parent_id"));
        if (current == null) {
            return null;
        }
        List<ApiRequest> siblings = apiRequestMapper.selectList(new QueryWrapper<ApiRequest>()
                .eq("project_id", projectId)
                .eq("parent_id", current.getParentId() == null ? 0 : current.getParentId())
                .eq("api_node", ApiNodeType.INTERFACE)
                .ne("id", current.getId())
                .isNull("source_drat_id")
                .select("id", "api_name", "request_method", "request_path")
                .last("limit 10"));
        if (siblings.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ApiRequest s : siblings) {
            sb.append("- ").append(s.getRequestMethod()).append(' ')
                    .append(s.getRequestPath()).append(' ').append(s.getApiName()).append('\n');
        }
        return new ContextBlocks.Block("L3", "同目录其他接口", sb.toString(), true);
    }
}
