package com.mokatest.platform.demos.ai.skill.apicase;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.context.ContextBlocks;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiNodeType;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import com.mokatest.platform.demos.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * L1 接口定义上下文：method/path/header/query/body/响应示例（不可裁剪）
 */
@Component
public class ApiDefinitionSource {

    @Autowired
    private ApiRequestMapper apiRequestMapper;

    public ContextBlocks.Block load(Long entityId, Integer projectId, Integer teamId) {
        ApiRequest api = apiRequestMapper.selectOne(new QueryWrapper<ApiRequest>()
                .eq("id", entityId)
                .eq("project_id", projectId)
                .eq("team_id", teamId)
                .eq("api_node", ApiNodeType.INTERFACE));
        if (api == null) {
            throw new BusinessException("接口不存在");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("接口名称：").append(api.getApiName()).append('\n');
        sb.append("请求：").append(api.getRequestMethod()).append(' ').append(api.getRequestPath()).append('\n');
        if (api.getRequestHeader() != null && !api.getRequestHeader().isEmpty()) {
            sb.append("请求头：").append(JSON.toJSONString(api.getRequestHeader())).append('\n');
        }
        if (api.getQuery() != null && !api.getQuery().isEmpty()) {
            sb.append("Query 参数：").append(JSON.toJSONString(api.getQuery())).append('\n');
        }
        if (api.getBody() != null) {
            sb.append("Body：").append(JSON.toJSONString(api.getBody())).append('\n');
        }
        if (api.getResponseExamples() != null && !api.getResponseExamples().isEmpty()) {
            String examples = JSON.toJSONString(api.getResponseExamples());
            sb.append("响应示例：").append(examples.length() > 1500 ? examples.substring(0, 1500) + "…" : examples).append('\n');
        }
        if (api.getApiResultAssert() != null && !api.getApiResultAssert().isEmpty()) {
            sb.append("已有断言：").append(JSON.toJSONString(api.getApiResultAssert())).append('\n');
        }
        return new ContextBlocks.Block("L1", "接口定义", sb.toString(), false);
    }
}
