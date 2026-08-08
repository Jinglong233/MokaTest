package com.mokatest.platform.demos.service;

import com.mokatest.platform.demos.domain.ui.Plan;
import com.mokatest.platform.demos.domain.ui.Report;

/**
 * Webhook 通知服务
 */
public interface WebhookService {

    /**
     * 发送计划执行完成通知
     *
     * @param plan   执行的计划
     * @param report 执行报告
     */
    void send(Plan plan, Report report);
}
