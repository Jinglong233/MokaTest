package com.mokatest.platform.demos.domain.ui.dto.record;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 单个定位候选
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordCandidateDTO {

    /**
     * 定位类型
     */
    private String locatorType;

    /**
     * 定位值
     */
    private String locatorValue;

    /**
     * 打分（越高越优先）
     */
    private Integer score;

}
