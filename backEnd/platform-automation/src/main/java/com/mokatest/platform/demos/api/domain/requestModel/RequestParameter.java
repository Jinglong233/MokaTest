package com.mokatest.platform.demos.api.domain.requestModel;

import com.mokatest.platform.demos.api.domain.apiEnum.ParameterType;
import lombok.Data;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @Description: 参数
 * @Author: JingLong
 * @DateTime: 2026/4/3 14:07
 */
@Data
public class RequestParameter {
    private String name;
    private String value;
    private ParameterType type;
    private String description;
    private boolean disabled;

    /**
     * 参数级 Mock 生成配置
     *
     * 当 {@code value = "{{__MOCK__}}"} 且本字段不为空时，执行器按此配置生成数据。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private MockConfig mockConfig;

    public RequestParameter() {
    }

    public RequestParameter(String name, String value) {
        this.name = name;
        this.value = value;
    }
}
