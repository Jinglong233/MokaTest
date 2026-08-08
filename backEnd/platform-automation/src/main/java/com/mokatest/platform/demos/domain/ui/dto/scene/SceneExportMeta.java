package com.mokatest.platform.demos.domain.ui.dto.scene;

import lombok.Data;

/**
 * 导出场景元数据
 */
@Data
public class SceneExportMeta {

    private String name;

    private String description;

    private String sceneCategory;

    private String sceneSetting;
}
