package com.mokatest.platform.demos.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * AI 模型接入配置（平台级 + 项目级，按作用域互斥生效）
 * @TableName ai_config
 */
@TableName("ai_config")
@Data
public class AiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置名（如：生产-GPT4o / 测试-DeepSeek） */
    private String configName;

    /** 提供方标识（openai 兼容） */
    private String provider;

    /** OpenAI 兼容端点，如 https://api.openai.com/v1 */
    private String baseUrl;

    /** API Key（AES 加密存储） */
    private String apiKey;

    /** 对话模型名 */
    private String chatModel;

    /** 向量模型名，未配置则知识库降级为关键词检索 */
    private String embeddingModel;

    /** 单次最大输出 tokens */
    private Integer maxTokens;

    /** 采样温度 */
    private BigDecimal temperature;

    /** 请求超时（毫秒） */
    private Integer timeoutMs;

    /** 是否启用：0-未启用 1-启用（全表唯一生效行，服务层保证） */
    private Integer enabled;

    /** 是否启用多模态（图片理解）：0-否 1-是 */
    private Integer visionEnabled;

    /** 是否启用 JSON 模式（response_format=json_object）：0-否 1-是 */
    private Integer supportJsonMode;

    /** 备注（配置用途说明） */
    private String remark;

    /** Thinking 模式：AUTO（按厂商适配）/ DISABLED（强制关闭）/ ENABLED（强制开启） */
    private String thinkingMode;

    /** 所属项目（NULL=平台档案） */
    private Integer projectId;

    private String createUserId;

    private String updateUserId;

    private Date createTime;

    private Date updateTime;
}
