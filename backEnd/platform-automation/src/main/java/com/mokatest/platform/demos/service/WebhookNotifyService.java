package com.mokatest.platform.demos.service;

import com.mokatest.platform.demos.domain.ui.Plan;
import com.mokatest.platform.demos.domain.ui.PlanWebhook;
import com.mokatest.platform.demos.domain.ui.Report;

/**
 * Webhook 通知发送服务接口
 * 
 * 负责在计划执行完成后，根据配置向第三方平台发送通知消息。
 * 与 {@link PlanWebhookService} 的区别：
 *   本接口只负责「发送」
 *   PlanWebhookService 负责「配置的 CRUD 管理」
 * 
 * 发送流程：
 * <ol>
 *   检查计划是否开启了 webhook_enabled
 *   查询该项目下所有启用的 Webhook 配置
 *   根据报告结果（SUCCESS/FAILURE）匹配 notifyOn
 *   逐个平台构造消息体并异步发送
 * </ol>
 * 
 * <b>容错原则：</b>任何发送异常只记录日志，绝不影响主流程。
 */
public interface WebhookNotifyService {

    /**
     * 发送计划执行完成通知
     * 
     * 根据计划关联的项目，查询所有启用的 Webhook 配置，逐一发送通知。
     * 若计划未开启通知开关，或项目下无配置，直接静默返回。
     *
     * @param plan   执行完成的计划对象（需包含 projectId、webhookEnabled）
     * @param report 执行报告对象（用于构造消息内容）
     */
    void send(Plan plan, Report report);

    /**
     * 向单个 Webhook 配置发送测试消息
     * 
     * 用于前端「测试发送」按钮，验证 URL 和密钥是否正确。
     *
     * @param planWebhook Webhook 配置
     * @return true = 发送成功（HTTP 200）；false = 发送失败
     */
    Boolean testSend(PlanWebhook planWebhook);
}
