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

    /**
     * 是否同步导入元素至元素库（按「同名+同主定位」查重复用，不存在则新建）
     */
    private Boolean syncElementsToLibrary;

    /**
     * 元素库目标目录 ID；缺省 = 与场景所属目录同名的元素库目录（不存在则自动创建）
     */
    private Integer elementParentId;

    /**
     * 幂等批次 ID（客户端生成的 UUID），重复提交返回首次导入结果
     */
    private String importBatchId;

    private List<SceneExportDTO> sceneDataList;
}
