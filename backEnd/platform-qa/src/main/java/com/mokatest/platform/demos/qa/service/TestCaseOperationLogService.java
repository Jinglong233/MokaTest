package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.TestCaseOperationLog;

/**
 * 用例操作日志 Service
 */
public interface TestCaseOperationLogService extends IService<TestCaseOperationLog> {

    /**
     * 按用例查询操作日志（按操作时间倒序）
     */
    SaResult listByCaseId(Integer caseId);

    /**
     * 记录操作日志（自动取当前登录人ID和昵称）
     */
    void log(Integer caseId, String operation, String fieldName, String oldValue, String newValue, String comment);

    /**
     * 记录评审投票日志（REVIEW_VOTE，带评审单ID，逐票留痕）
     */
    void logReviewVote(Integer caseId, Integer reviewId, String oldConclusion, String newConclusion, String comment);
}
