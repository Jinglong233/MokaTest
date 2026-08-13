package com.mokatest.platform.demos.operationlog.service;

import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.operationlog.domain.SysLoginLog;

/**
 * 登录日志 Service
 */
public interface SysLoginLogService {

    /**
     * 异步保存登录日志
     */
    void asyncSave(SysLoginLog log);

    /**
     * 记录一次登录/登出事件
     *
     * @param operation LOGIN / LOGOUT
     * @param userId    用户ID（可为 null）
     * @param username  登录输入的用户名
     * @param nickname  昵称（可为 null）
     * @param success   是否成功
     * @param message   失败原因（成功时可为 null）
     */
    void record(String operation, Long userId, String username, String nickname, boolean success, String message);

    /**
     * 分页查询登录日志
     */
    SaResult list(String operation, String status, String keyword, String ip,
                  String startTime, String endTime, Integer pageNum, Integer pageSize);

    /**
     * 删除单条登录日志
     */
    void delete(Long id);

    /**
     * 批量删除登录日志
     */
    void batchDelete(java.util.List<Long> ids);

    /**
     * 清空登录日志
     */
    void clear();

    /**
     * 按筛选条件导出 Excel
     */
    void export(String operation, String status, String keyword, String ip,
                String startTime, String endTime, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException;
}
