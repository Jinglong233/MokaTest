package com.mokatest.platform.demos.api.domain.vo;

import com.mokatest.platform.demos.api.domain.ApiRequest;

import java.util.List;

/**
 * @Description:
 * @Author: JingLong
 * @DateTime: 2026/4/3 17:29
 */
public class ApiRequestVO extends ApiRequest {
    private List<ApiRequestVO> children;
}
