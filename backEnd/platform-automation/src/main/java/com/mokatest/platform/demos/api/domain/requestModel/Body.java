package com.mokatest.platform.demos.api.domain.requestModel;

import com.mokatest.platform.demos.api.domain.apiEnum.BodyMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Description: 请求体model
 * @Author: JingLong
 * @DateTime: 2026/4/3 11:42
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Body {
    private BodyMode mode;
    private List<RequestParameter> formData;
    private List<RequestParameter> xWwwFormUrlencoded;
    private String json;
    private String xml;

    /**
     * 请求体结构绑定（mode=JSON 时生效，对齐 Apifox Body 引用数据模型）：
     * 配置后执行时按定义生成 JSON 请求体，优先于手写 json。
     * 复用 ResponseSchema 模型（validateEnabled 对请求侧无意义，忽略）。
     */
    private ResponseSchema schemaBinding;
}
