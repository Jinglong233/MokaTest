package com.mokatest.platform.demos.api.http.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 变量追踪记录
 *
 * 记录请求执行过程中变量替换的完整链路，帮助用户排查变量是否正确替换
 */
@Data
@Builder
public class VariableTrack {

    /**
     * 变量来源：环境变量、全局变量等
     */
    @Builder.Default
    private Map<String, String> variableSources = new HashMap<>();

    /**
     * URL 路径替换记录
     */
    private ReplaceRecord urlReplace;

    /**
     * 请求头替换记录列表
     */
    @Builder.Default
    private List<ReplaceRecord> headerReplaces = new ArrayList<>();

    /**
     * Cookie 替换记录列表
     */
    @Builder.Default
    private List<ReplaceRecord> cookieReplaces = new ArrayList<>();

    /**
     * Query 参数替换记录列表
     */
    @Builder.Default
    private List<ReplaceRecord> queryReplaces = new ArrayList<>();

    /**
     * Body 替换记录
     */
    private ReplaceRecord bodyReplace;

    /**
     * 提取的变量（执行后产生）
     */
    @Builder.Default
    private Map<String, Object> extractedVariables = new HashMap<>();

    /**
     * 未匹配的变量（有占位符但找不到对应值）
     */
    @Builder.Default
    private List<String> unmatchedVariables = new ArrayList<>();

    /**
     * 单次替换记录
     */
    @Data
    @Builder
    public static class ReplaceRecord {
        // 参数位置/名称标识
        private String name;
        // 替换前内容
        private String before;
        // 替换后内容
        private String after;
        // 该内容中发现的变量列表（所有占位符中的变量名）
        @Builder.Default
        private List<String> variables = new ArrayList<>();
        // 该内容中未匹配的变量列表（找不到对应值的变量名）
        @Builder.Default
        private List<String> unmatchedVariables = new ArrayList<>();
    }
}
