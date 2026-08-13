package com.mokatest.platform.demos.operationlog.dto;

import com.mokatest.platform.demos.domain.ui.dto.queryDto.BasePageQueryDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志查询参数
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationLogQueryDTO extends BasePageQueryDTO {

    private String module;

    private String operateType;

    private String targetType;

    private Integer operatorId;

    private String keyword;

    /**
     * 状态筛选：SUCCESS（responseCode=200）/ FAIL（responseCode!=200）
     */
    private String status;

    /**
     * IP 筛选（模糊）
     */
    private String ip;

    private String startTime;

    private String endTime;
}
