package com.mokatest.platform.demos.domain.ui.dto.scene;

import lombok.Data;

import java.util.List;

/**
 * 批量场景 JSON 导入请求
 */
@Data
public class SceneBatchImportDTO {

    private Integer parentId;

    private String projectId;

    /**
     * 导入目标分类（UI / API），由前端当前页面传入；为空时回退到场景 meta 中的分类
     */
    private String sceneCategory;

    private List<SceneExportDTO> sceneDataList;
}
