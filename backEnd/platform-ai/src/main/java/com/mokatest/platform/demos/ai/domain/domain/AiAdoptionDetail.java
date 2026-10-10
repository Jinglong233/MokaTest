package com.mokatest.platform.demos.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * AI 采纳明细（独立表，record_no + draft_id 唯一索引兜底防并发重复入库）
 */
@TableName("ai_adoption_detail")
@Data
public class AiAdoptionDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 生成记录编号 */
    private String recordNo;

    /** 草稿ID */
    private String draftId;

    /** 入库后的用例ID */
    private Integer caseId;

    private Date createTime;
}
