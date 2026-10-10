package com.mokatest.platform.demos.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 调用用量日志（不记录 prompt 全文）
 * @TableName ai_usage_log
 */
@TableName("ai_usage_log")
@Data
public class AiUsageLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调用用户ID */
    private String userId;

    /** 团队ID */
    private Integer teamId;

    /** 项目ID（系统级调用为 NULL，如连通性测试） */
    private Integer projectId;

    /** 场景：GENERATE_CASE/GENERATE_API_CASE/REPORT_SUMMARY/BUG_ANALYSIS/EMBEDDING/CONFIG_TEST/REVIEW_EXPERT/CASE_REFINE/FAILURE_TRIAGE */
    private String scene;

    /** 消耗 tokens（totalTokenCount，可能为空） */
    private Integer tokens;

    /** 输入 token 数（ChatModelListener 提供，分拆计费用） */
    private Integer inputTokens;

    /** 输出 token 数 */
    private Integer outputTokens;

    /** 使用的模型名 */
    private String modelName;

    /** 结束原因（STOP/LENGTH/CONTENT_FILTER 等） */
    private String finishReason;

    /** 耗时（毫秒） */
    private Integer durationMs;

    /** 是否成功：0-失败 1-成功 */
    private Integer success;

    /** 失败原因摘要 */
    private String errorMsg;

    /** 入参摘要（不含 prompt 全文） */
    private String promptSummary;

    private Date createTime;
}
