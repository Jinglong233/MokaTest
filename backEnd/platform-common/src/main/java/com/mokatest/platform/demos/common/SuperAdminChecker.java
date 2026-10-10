package com.mokatest.platform.demos.common;

/**
 * 超级管理员校验接口（platform-ai 的 AiConfigServiceImpl 依赖此接口，
 * 实现由 platform-automation 提供，避免 platform-ai 反向依赖 automation）
 */
public interface SuperAdminChecker {
    boolean isSuperAdmin(String loginId);
}
