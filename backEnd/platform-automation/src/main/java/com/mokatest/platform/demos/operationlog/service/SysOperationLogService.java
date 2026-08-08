package com.mokatest.platform.demos.operationlog.service;

import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.operationlog.domain.SysOperationLog;
import com.mokatest.platform.demos.operationlog.dto.OperationLogQueryDTO;

/**
 * 系统操作日志 Service
 */
public interface SysOperationLogService {

    /**
     * 异步保存操作日志
     */
    void asyncSave(SysOperationLog log);

    /**
     * 分页查询操作日志
     */
    SaResult list(OperationLogQueryDTO query);

    /**
     * 操作日志详情
     */
    SaResult detail(Long id);
}
