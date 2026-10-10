package com.mokatest.platform.demos.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * AI 自定义 Skill 定义（项目级，用户通过 UI 创建）
 * @TableName ai_skill_definition
 */
@TableName("ai_skill_definition")
@Data
public class AiSkillDefinition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer projectId;

    /** 唯一标识，如 perf_test_plan */
    private String scene;

    /** 显示名，如 性能测试方案 */
    private String name;

    /** 用户写的说明 */
    private String description;

    /** Skill 的 system prompt */
    private String systemPrompt;

    /** 逗号分隔的 ContextSource code 列表，如 knowledge,requirement_core */
    private String contextSources;

    /** 模型温度 */
    private BigDecimal temperature;

    /** 最大输出 token */
    private Integer maxTokens;

    /** 0-禁用 1-启用 */
    private Integer enabled;

    /** 是否允许使用平台工具：1-允许 0-纯对话 */
    private Integer toolsEnabled;

    private Integer createBy;

    private Date createTime;

    private Date updateTime;
}
