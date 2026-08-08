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

    /** 场景：GENERATE_CASE/GENERATE_API_CASE/REPORT_SUMMARY/BUG_ANALYSIS/EMBEDDING/CONFIG_TEST */
    private String scene;

    /** 消耗 tokens（响应 usage.total_tokens，可能为空） */
    private Integer tokens;

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
