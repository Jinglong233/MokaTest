package com.mokatest.platform.demos.domain.ui.dto.record;

import lombok.Data;

import java.util.List;

/**
 * /record/save 入参
 */
@Data
public class RecordSaveDTO {

    /**
     * 项目 ID
     */
    private String projectId;

    /**
     * 场景名称
     */
    private String name;

    /**
     * 目标目录 ID，0 表示根目录
     */
    private Integer parentId;

    /**
     * 场景描述
     */
    private String description;

    /**
     * 确认后的步骤列表
     */
    private List<RecordStepDraftVO> steps;

}
