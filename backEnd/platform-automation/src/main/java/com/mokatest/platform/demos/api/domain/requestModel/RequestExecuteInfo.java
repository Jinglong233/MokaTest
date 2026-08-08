package com.mokatest.platform.demos.api.domain.requestModel;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * API 请求执行环境配置模型
 *
 * 功能说明：定义 API 接口调试/执行时使用的环境信息，包含基础 URL、环境变量、环境 Cookie、环境 Header 等。
 * 该对象作为 ApiRequest 的 envInfo 字段存储
 *
 * 使用场景：
 *   - 前端调试时选择环境，将环境信息随接口配置一起发送到后端
 *   - 后端执行请求时从 envInfo 中获取 baseUrl 拼接完整 URL
 *   - 后端执行请求时从 envVariables 获取变量值进行 ${var} / {{var}} 替换
 *   - 后端执行请求时从 envCookies/envHeaders 合并环境级 Cookie/Header 到请求中
 *
 * 配置示例（JSON 存储格式）：
 * {
 *   "envId": 1,
 *   "envName": "测试环境",
 *   "baseUrl": "https://api-test.example.com",
 *   "envVariables": {
 *     "token": "abc123",
 *     "userId": "456"
 *   },
 *   "envCookies": [
 *     {"name": "sessionId", "value": "xyz789", "disabled": false}
 *   ],
 *   "envHeaders": [
 *     {"name": "X-App-Version", "value": "1.0.0", "disabled": false}
 *   ]
 * }
 *
 * @author JingLong
 * @see ApiRequest
 * @since 2026-04-03
 */
@Data
public class RequestExecuteInfo {

    /**
     * 关联的数据库配置（预留字段，暂未使用）
     */
    private String database;

    /**
     * 环境 ID
     *
     * 对应前端环境管理中的环境唯一标识
     */
    private Integer envId;

    /**
     * 环境名称
     *
     * 如："开发环境"、"测试环境"、"生产环境"
     */
    private String envName;

    /**
     * 环境基础 URL
     *
     * 与接口的 requestPath 拼接为完整请求 URL
     *
     * 示例：https://api.example.com
     */
    private String baseUrl;

    /**
     * 服务器配置参数（预留字段，暂未使用）
     *
     * 计划用于存储服务器相关的额外配置，如超时时间、代理设置等
     */
    private ServeParameter serve;

    /**
     * 环境变量映射表
     *
     * 用途：在请求执行前，替换 URL、Header、Cookie、Query、Body 中的变量占位符
     *
     * 变量引用语法：${变量名} 或 {{变量名}}
     *
     * 示例：{"token": "abc123", "userId": "456"}
     */
    private Map<String, String> envVariables;

    /**
     * 环境级 Cookie 列表
     *
     * 用途：自动附加到当前环境的所有请求中，无需在每个接口中单独配置
     *
     * 实现说明：
     *   - 在请求执行时，将环境 Cookie 与接口自身的 Cookie 合并
     *   - 接口自身的 Cookie 优先级更高（后添加，可覆盖环境级的同名 Cookie）
     *   - disabled=true 的 Cookie 会被跳过
     */
    private List<RequestParameter> envCookies;

    /**
     * 环境级 Header 列表
     *
     * 用途：自动附加到当前环境的所有请求中，无需在每个接口中单独配置
     *
     * 实现说明：
     *   - 在请求执行时，将环境 Header 与接口自身的 Header 合并
     *   - 接口自身的 Header 优先级更高（后添加，可覆盖环境级的同名 Header）
     *   - disabled=true 的 Header 会被跳过
     */
    private List<RequestParameter> envHeaders;

}
