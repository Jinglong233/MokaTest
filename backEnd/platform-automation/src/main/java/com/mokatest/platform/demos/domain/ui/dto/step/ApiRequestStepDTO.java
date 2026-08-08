package com.mokatest.platform.demos.domain.ui.dto.step;

import com.mokatest.platform.demos.api.domain.ApiRequest;
import lombok.Data;

/**
 * API请求步骤详情DTO
 *
 * 说明：场景中的API步骤使用副本模式（不是引用）。
 * - apiRequestId：仅用于追溯来源（可为空）
 * - apiConfig：完整的API配置副本，包含请求方法、路径、参数、Body、断言、提取等
 * - 修改场景里的API步骤只影响当前场景的副本，不影响原始接口/用例
 */
@Data
public class ApiRequestStepDTO {

    /**
     * 来源API接口/用例ID（追溯用，可为空）
     */
    private Integer apiRequestId;

    /**
     * API名称（展示用）
     */
    private String apiName;

    /**
     * 完整的API配置副本
     * 包含：requestMethod、requestPath、requestHeader、body、断言、提取、脚本等
     */
    private ApiRequest apiConfig;

}
