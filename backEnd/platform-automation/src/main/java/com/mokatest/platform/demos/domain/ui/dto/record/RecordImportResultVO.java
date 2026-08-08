package com.mokatest.platform.demos.domain.ui.dto.record;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * /record/import 返回结果
 */
@Data
public class RecordImportResultVO {

    /**
     * 转换后的草稿步骤
     */
    private List<RecordStepDraftVO> steps;

    /**
     * 警告信息（如 iframe 步骤跳过、定位缺失等）
     */
    private List<String> warnings;

    /**
     * 跳过的事件统计
     */
    private Map<String, Integer> skipped;

}
