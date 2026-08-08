package com.mokatest.platform.demos.domain.ui.dto.scene;

import lombok.Data;

import java.util.List;

/**
 * 批量场景导出结构
 */
@Data
public class SceneBatchExportDTO {

    private String version = "1.0";

    private String exportType = "scene-batch";

    private String exportedAt;

    private List<SceneExportDTO> scenes;
}
