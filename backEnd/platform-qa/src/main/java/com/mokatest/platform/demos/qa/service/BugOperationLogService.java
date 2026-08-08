package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.BugOperationLog;

public interface BugOperationLogService extends IService<BugOperationLog> {

    SaResult listByBug(Integer bugId);

    void logOperation(Integer bugId, String fieldName, String oldValue, String newValue, Integer operatorId);
}
