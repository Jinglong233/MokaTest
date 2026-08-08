package com.mokatest.platform.demos.domain.ui.dto.queryDto;

import lombok.Data;

/**
 * @Author JingLong
 * @Description 报告查询DTO
 * @Date 2025/11/6 17:52
 **/
@Data
public class ReportQueryDTO extends BasePageQueryDTO {


    /**
     * 所属项目id
     */
    private Integer projectId;

    /**
     * 执行者id
     */
    private String executionUserId;

    /**
     * 执行者姓名
     */
    private String executionUserName;

    /**
     * 任务类型
     */
    private String taskType;


    /**
     * 状态
     */
    private Integer status;

    /**
     * 关联计划id
     */
    private Integer planId;

    /**
     * 计划名称
     */
    private String planName;

    /**
     * 报告名称
     */
    private String reportName;

    /**
     * 场景列表
     */
    private Object scenes;

}
