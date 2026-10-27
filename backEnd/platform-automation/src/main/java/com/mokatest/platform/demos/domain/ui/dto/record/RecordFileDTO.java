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
     * 文件格式版本：1.0 原始协议；1.1 事件携带完整 locatorCandidates（含 TEXT_CONTAINS 类型与 score）
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
