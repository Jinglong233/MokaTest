package com.mokatest.platform.demos.ai.skill;

/**
 * 上下文来源抽象
 *
 * 每个实现负责从一个数据源（需求详情、血缘、同模块需求、已有用例、历史BUG、知识库…）
 * 抽取上下文块。实现必须严格遵守项目隔离：一切查询带 project_id，projectId 来自
 * 请求头上下文（ProjectContextHolder），不接受前端 body 传参。
 *
 * 扩展新数据源（如未来的需求附件）只需新增实现并注册为 Spring Bean，主流程零改动。
 */
public interface ContextSource {

    /**
     * 来源标识（用于前端开关 contextOptions 的 key）
     */
    String code();

    /**
     * 所属层级：L0/L1/L2/L3/L4/L5
     */
    String level();

    /**
     * 是否默认启用（前端上下文选项的默认勾选态）
     */
    default boolean defaultEnabled() {
        return true;
    }

    /**
     * 是否支持指定场景（scene 与 AiSkill.scene() 对应）
     */
    default boolean supports(String scene) {
        return true;
    }

    /**
     * 抽取上下文块；无数据时返回 null
     */
    ContextBlock load(SkillRequest request);
}
