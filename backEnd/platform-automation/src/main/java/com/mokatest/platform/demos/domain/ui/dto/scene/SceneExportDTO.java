package com.mokatest.platform.demos.domain.ui.dto.scene;

import lombok.Data;

import java.util.List;

/**
 * UI/API 场景导出数据结构
 */
@Data
public class SceneExportDTO {

    private String version = "1.0";

    private String exportType = "scene";

    private String exportedAt;

    private SceneExportMeta meta;

    private List<StepExportDTO> steps;
}
