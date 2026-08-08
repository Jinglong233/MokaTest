package com.mokatest.platform.demos.api.domain.apiEnum;

/**
 * 规则来源枚举
 *
 * 用于标识断言规则和数据提取规则的配置来源，便于在调试结果中区分
 * 规则是来自全局配置、场景配置、环境配置还是接口自身配置。
 *
 * @author JingLong
 * @since 2026-05-29
 */
public enum RuleSource {
    /**
     * 全局配置：在团队全局变量中配置的规则，对该团队下所有接口生效
     */
    GLOBAL,

    /**
     * 环境配置：在环境设置中配置的规则（预留扩展）
     */
    ENVIRONMENT,

    /**
     * 场景配置：在API场景设置中配置的场景级规则，对该场景下所有步骤生效
     */
    SCENE,

    /**
     * API接口自身配置：在单个接口或接口用例中配置的规则
     */
    API
}
