package com.mokatest.platform.demos.ai.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 知识库文档（项目级，隔离边界 project_id）
 */
@TableName("knowledge_doc")
@Data
public class KnowledgeDoc {

    /** 索引状态：待索引 */
    public static final String STATUS_PENDING = "PENDING";
    /** 索引状态：索引中 */
    public static final String STATUS_INDEXING = "INDEXING";
    /** 索引状态：就绪 */
    public static final String STATUS_READY = "READY";
    /** 索引状态：失败（可手动重建） */
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属项目（隔离边界） */
    private Integer projectId;

    /** 文档标题 */
    private String title;

    /** 文档类型：MD/TXT（PDF 预留） */
    private String docType;

    /** 文档内容（纯文本/markdown） */
    private String content;

    /** 索引状态：PENDING/INDEXING/READY/FAILED */
    private String indexStatus;

    /** AI 引用次数（生成时检索命中累计） */
    private Integer citeCount;

    private String createUserId;

    private String updateUserId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDeleted;

    private Date deletedAt;
}
