package com.mokatest.platform.demos.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 生成记录（会话锚点 + 入库依据）
 *
 * 会话隔离规则：采纳/保存只传 recordNo，后端按记录锚点读取 entityId 与 projectId，
 * 不信任前端传参；跨项目 recordNo 一律拒绝。
 *
 * @TableName ai_generation_record
 */
@TableName("ai_generation_record")
@Data
public class AiGenerationRecord {

    /** 状态：有效 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态：已过期（24h） */
    public static final String STATUS_EXPIRED = "EXPIRED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 记录编号（UUID，对外暴露） */
    private String recordNo;

    /** 生成用户ID */
    private String userId;

    /** 团队ID */
    private Integer teamId;

    /** 项目ID（隔离边界） */
    private Integer projectId;

    /** 场景：GENERATE_CASE/GENERATE_API_CASE/REPORT_SUMMARY/BUG_CLUSTER_INSIGHT */
    private String scene;

    /** 锚定实体ID（需求ID/接口ID/报告ID；项目级场景为 NULL） */
    private Long entityId;

    /** 输入摘要（含引用的知识库 chunk 记录） */
    private String inputSummary;

    /** 输出快照（JSON，追加生成时累积） */
    private String outputSnapshot;

    /** 采纳登记（哪些条目已入库、入库后的实体ID） */
    private String adoptedDetail;

    /** 状态：ACTIVE/EXPIRED */
    private String status;

    private Date createTime;

    private Date expireTime;
}
