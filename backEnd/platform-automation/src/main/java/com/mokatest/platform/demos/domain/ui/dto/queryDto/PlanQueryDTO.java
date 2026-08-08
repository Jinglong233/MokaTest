package com.mokatest.platform.demos.domain.ui.dto.queryDto;

/**
 * @Author JingLong
 * @Description 计划查询DTO
 * @Date 2025/11/5 11:54
 **/
public class PlanQueryDTO {


    /**
     * 关联计划配置信息
     */
    private Integer configId;

    /**
     * 计划名称
     */
    private String planName;

    /**
     * cron表达式
     */
    // todo 待处理
    private String cronExpression;


    /**
     * 场景执行类型（顺序、并发）
     */
    private Object executionType;

    /**
     * 参数
     */
    private Object params;

    /**
     * 任务类型
     */
    private Object taskType;

    /**
     * 状态
     */
    private Object status;
}
