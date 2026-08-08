package com.mokatest.platform.demos.domain.ui.dto.other;

import lombok.Data;

import java.util.List;

/**
 * @Author JingLong
 * @Description 添加已存在场景步骤DTO
 * @Date 2026/1/10 11:56
 **/
@Data
public class ImportExistSceneStepDTO {
    /**
     * 目标场景Id
     */
    private Integer targetSceneId;

    /**
     * 需要添加的场景id集合（旧逻辑：导入整个场景的所有步骤）
     */
    private List<Integer> sourceSceneIds;

    /**
     * 需要添加的具体步骤id集合（新逻辑：精确导入指定步骤及其子步骤）
     */
    private List<Integer> sourceStepIds;
}
