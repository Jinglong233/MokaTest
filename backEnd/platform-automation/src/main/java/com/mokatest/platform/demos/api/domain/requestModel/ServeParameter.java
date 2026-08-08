package com.mokatest.platform.demos.api.domain.requestModel;

import lombok.Data;

/**
 * @Description: 服务参数
 * @Author: JingLong
 * @DateTime: 2026/4/3 14:35
 */
@Data
public class ServeParameter {

    // id
    private Integer id;
    // 服务名称
    private String name;
    // 服务地址
    private String address;
}
