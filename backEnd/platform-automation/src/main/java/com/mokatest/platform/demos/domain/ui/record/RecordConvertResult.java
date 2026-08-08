package com.mokatest.platform.demos.domain.ui.record;

import com.mokatest.platform.demos.domain.ui.dto.record.RecordStepDraftVO;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 录制事件转换结果
 */
@Data
public class RecordConvertResult {

    /**
     * 草稿步骤
     */
    private List<RecordStepDraftVO> steps;

    /**
     * 警告信息
     */
    private List<String> warnings;

    /**
     * 跳过统计
     */
    private Map<String, Integer> skipped;

}
