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
     * 继续录制：已有场景 ID，步骤追加到该场景末尾；为空表示新建场景
     */
    private Integer sceneId;

    /**
     * 幂等批次 ID（客户端生成的 UUID），重复提交返回首次保存的场景 ID
     */
    private String batchId;

    /**
     * 是否同步步骤内元素至元素库（按「同名+同主定位」查重复用，不存在则新建）
     */
    private Boolean syncElementsToLibrary;

    /**
     * 元素库目标目录 ID；缺省 = 与场景所属目录同名的元素库目录（不存在则自动创建）
     */
    private Integer elementParentId;

    /**
     * 确认后的步骤列表
     */
    private List<RecordStepDraftVO> steps;

}
