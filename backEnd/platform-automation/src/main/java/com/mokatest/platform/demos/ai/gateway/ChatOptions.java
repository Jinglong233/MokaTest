package com.mokatest.platform.demos.ai.gateway;

import lombok.Data;

/**
 * 单次 AI 调用的上下文选项
 */
@Data
public class ChatOptions {

    /** 场景标识（写 ai_usage_log.scene），必填 */
    private String scene;

    /** 调用用户ID（写日志） */
    private String userId;

    /** 团队ID（写日志） */
    private Integer teamId;

    /** 项目ID（写日志；系统级调用为 null） */
    private Integer projectId;

    /** 入参摘要（写日志，不含 prompt 全文） */
    private String promptSummary;

    /** 覆盖默认 maxTokens，null 用配置值 */
    private Integer maxTokens;

    /** 覆盖默认 temperature，null 用配置值 */
    private Double temperature;

    /** 覆盖默认超时（毫秒），null 用配置值；轻量调用（如意图分类）可设短一些快速失败 */
    private Integer timeoutMs;
}
