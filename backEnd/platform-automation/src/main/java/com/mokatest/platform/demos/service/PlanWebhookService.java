package com.mokatest.platform.demos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.domain.ui.PlanWebhook;

import java.util.List;

/**
 * Webhook 配置 Service 接口
 * 
 * 提供 Webhook 配置的增删改查及测试发送能力。
 * 与 {@link WebhookNotifyService} 的区别：
 *   本接口负责配置的 CRUD 管理
 *   WebhookNotifyService 负责执行完成后的实际发送
 */
public interface PlanWebhookService extends IService<PlanWebhook> {

    /**
     * 根据项目ID查询 Webhook 配置列表
     *
     * @param projectId 项目ID
     * @return 该项目下的配置列表
     */
    List<PlanWebhook> listByProjectId(Integer projectId);

    /**
     * 新增或更新 Webhook 配置
     * 
     * id 为空则新增，有值则更新。同时自动填充创建人/更新人信息。
     *
     * @param planWebhook 配置对象
     * @return 是否成功
     */
    Boolean saveOrUpdateWebhook(PlanWebhook planWebhook);

    /**
     * 测试发送
     * 
     * 向指定的 Webhook 配置发送一条测试消息，用于验证 URL 和密钥是否正确。
     * 不影响任何计划执行，仅做连通性测试。
     *
     * @param planWebhook 配置对象（通常从前端传入，可能尚未保存到数据库）
     * @return 是否发送成功
     */
    Boolean testSend(PlanWebhook planWebhook);

    /**
     * 删除 Webhook 配置
     * 
     * 执行逻辑删除，记录删除时间。
     *
     * @param id 配置ID
     * @return 是否成功
     */
    Boolean deleteWebhook(Integer id);
}
