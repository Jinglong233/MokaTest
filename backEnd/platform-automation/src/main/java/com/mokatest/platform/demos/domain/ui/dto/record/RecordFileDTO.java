package com.mokatest.platform.demos.domain.ui.dto.record;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 录制器导出的 JSON 文件根结构
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordFileDTO {

    /**
     * 文件格式版本，当前为 1.0
     */
    private String version;

    /**
     * 生成器标识，如 mokaTest-recorder
     */
    private String generator;

    /**
     * 录制时间（ISO-8601）
     */
    private String recordedAt;

    /**
     * 起始 URL
     */
    private String startUrl;

    /**
     * 跳过的事件统计
     */
    private Map<String, Integer> skipped;

    /**
     * 录制事件列表
     */
    private List<RecordEventDTO> events;

}
