package com.mokatest.platform.demos.domain.ui.dto.scene;

import lombok.Data;

/**
 * 导出步骤数据结构
 */
@Data
public class StepExportDTO {

    /**
     * 原步骤ID，仅用于导入时重建 parentId 映射
     */
    private Integer id;

    private String stepType;

    private String stepName;

    private String description;

    private Integer parentId;

    private Integer orderIndex;

    private Integer isDisable;

    private Object stepDetail;
}
