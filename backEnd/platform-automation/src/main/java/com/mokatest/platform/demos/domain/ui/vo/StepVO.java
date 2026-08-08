package com.mokatest.platform.demos.domain.ui.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author JingLong
 * @Description 场景视图对象
 * @Date 2025/8/6 17:19
 **/
@Data
public class StepVO {
    private Integer id;

    /**
     * 步骤类型
     */
    private String stepType;

    /**
     * 步骤名称
     */
    private String stepName;

    /**
     * 是否禁用
     */
    private Integer isDisable;

    /**
     * 步骤描述
     */
    private String description;

    /**
     * 父步骤ID
     */
    private Integer parentId;

    /**
     * 执行顺序
     */
    private Integer orderIndex;

    /**
     * 所属项目ID
     */
    private String projectId;

    /**
     * 所属场景ID
     */
    private String scenarioId;

    /**
     * 关联元素ID
     */
    private String elementId;

    /**
     * 自定义元素类型
     */
    private Object customElementType;

    /**
     * 自定义元素值
     */
    private String customElementValue;

    /**
     * 步骤详情（含 API 请求步骤的 apiConfig、提取/断言规则等）。
     * 供前端在步骤列表上展示「本步提取的变量」等数据流信息，无需逐个拉详情。
     */
    private Object stepDetail;


    /**
     * 子节点
     */
    private List<StepVO> children;
}
