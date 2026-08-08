package com.mokatest.platform.demos.api.domain.requestModel;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * API场景级配置
 *
 * 说明：作用域为当前API场景，该场景下所有API步骤共享。
 * 优先级：接口配置 > 场景配置 > 环境配置
 *
 * 执行时合并逻辑：
 * 1. 先应用环境配置的Cookie/Header/变量
 * 2. 再用场景配置的Cookie/Header/变量覆盖（同名覆盖）
 * 3. 最后用接口自身的Cookie/Header/变量覆盖（同名覆盖）
 */
@Data
public class ApiSceneConfig {

    /**
     * 场景级Header列表
     * 优先级高于环境Header，低于接口自身Header
     */
    private List<RequestParameter> sceneHeaders;

    /**
     * 场景级Cookie列表
     * 优先级高于环境Cookie，低于接口自身Cookie
     */
    private List<RequestParameter> sceneCookies;

    /**
     * 场景级变量列表
     * 与全局变量结构一致：name/value/description/disabled
     * 优先级高于环境变量，低于接口自身变量
     */
    private List<RequestParameter> sceneVariables;

    /**
     * 场景级断言列表
     * 该场景下所有API步骤执行后都会执行这些断言
     */
    private List<AssertParameter> sceneAssertions;

    /**
     * 关联的环境配置ID
     * 场景调试时，会自动将该环境的 baseUrl、变量、Header、Cookie 合并到每个 API 步骤中
     */
    private Integer environmentId;

    /**
     * 关联的环境配置名称（冗余字段，方便前端展示）
     */
    private String environmentName;

    public ApiSceneConfig() {
        this.sceneHeaders = new ArrayList<>();
        this.sceneCookies = new ArrayList<>();
        this.sceneVariables = new ArrayList<>();
        this.sceneAssertions = new ArrayList<>();
    }
}
