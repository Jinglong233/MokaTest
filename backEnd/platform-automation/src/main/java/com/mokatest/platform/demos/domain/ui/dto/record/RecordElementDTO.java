package com.mokatest.platform.demos.domain.ui.dto.record;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 录制事件中的元素信息
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordElementDTO {

    /**
     * 元素展示名称
     */
    private String elementName;

    /**
     * 定位候选列表
     */
    private List<RecordCandidateDTO> candidates;

}
