package com.mokatest.platform.demos.ai.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.List;

/**
 * 知识库分块（embedding 以 JSON 数组存储；未配置向量模型时为 NULL，降级关键词检索）
 */
@TableName(value = "knowledge_chunk", autoResultMap = true)
@Data
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档ID */
    private Long docId;

    /** 所属项目（冗余，隔离边界） */
    private Integer projectId;

    /** 分块文本 */
    private String chunkText;

    /** 向量（未配置向量模型时为 NULL） */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Float> embedding;

    /** 块序号 */
    private Integer chunkIndex;

    /** 估算 token 数 */
    private Integer tokenCount;
}
