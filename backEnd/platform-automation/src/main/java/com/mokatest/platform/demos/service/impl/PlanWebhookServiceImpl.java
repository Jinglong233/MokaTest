package com.mokatest.platform.demos.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.dev33.satoken.stp.StpUtil;
import com.mokatest.platform.demos.domain.ui.PlanWebhook;
import com.mokatest.platform.demos.mapper.PlanWebhookMapper;
import com.mokatest.platform.demos.service.PlanWebhookService;
import com.mokatest.platform.demos.service.WebhookNotifyService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * Webhook 配置 Service 实现
 * 
 * 提供 Webhook 配置的增删改查及测试发送能力。
 * 
 * <b>创建/更新人自动填充：</b>使用 Sa-Token 的 {@link StpUtil#getLoginIdAsInt()} 获取当前登录用户 ID。
 * 若用户未登录（如定时任务场景），createUserId 可能为 null，不影响功能。
 *
 * @see PlanWebhookService
 */
@Slf4j
@Service
public class PlanWebhookServiceImpl extends ServiceImpl<PlanWebhookMapper, PlanWebhook> implements PlanWebhookService {

    @Resource
    private PlanWebhookMapper planWebhookMapper;

    @Resource
    private WebhookNotifyService webhookNotifyService;

    /**
     * 根据项目ID查询 Webhook 配置列表
     *
     * @param projectId 项目ID
     * @return 按创建时间倒序排列的配置列表
     */
    @Override
    public List<PlanWebhook> listByProjectId(Integer projectId) {
        return planWebhookMapper.selectByProjectId(projectId);
    }

    /**
     * 新增或更新 Webhook 配置
     * 
     * 判断逻辑：
     *   id 为空 → 新增，填充 createTime、createUserId
     *   id 不为空 → 更新，填充 updateTime、updateUserId
     *
     * @param planWebhook 配置对象
     * @return true = 操作成功
     */
    @Override
    public Boolean saveOrUpdateWebhook(PlanWebhook planWebhook) {
        if (planWebhook == null) {
            throw new IllegalArgumentException("Webhook 配置不能为空");
        }

        // 统一清理 URL 首尾空格，避免用户复制时带空白字符
        if (planWebhook.getUrl() != null) {
            planWebhook.setUrl(planWebhook.getUrl().trim());
        }
        if (planWebhook.getSecret() != null) {
            planWebhook.setSecret(planWebhook.getSecret().trim());
        }

        // 填充当前登录用户ID（未登录时可能为 null，不影响）
        Integer currentUserId = null;
        try {
            currentUserId = StpUtil.getLoginIdAsInt();
        } catch (Exception e) {
            log.debug("获取当前登录用户ID失败，可能未登录");
        }

        if (planWebhook.getId() == null) {
            // 新增
            planWebhook.setCreateTime(new Date());
            planWebhook.setUpdateTime(new Date());
            planWebhook.setCreateUserId(currentUserId);
            planWebhook.setUpdateUserId(currentUserId);
            return planWebhookMapper.insert(planWebhook) > 0;
        } else {
            // 更新
            planWebhook.setUpdateTime(new Date());
            planWebhook.setUpdateUserId(currentUserId);
            return planWebhookMapper.updateById(planWebhook) > 0;
        }
    }

    /**
     * 测试发送
     * 
     * 调用 {@link WebhookNotifyService#testSend} 进行连通性验证。
     * 测试消息为固定文本，不依赖任何计划或报告数据。
     *
     * @param planWebhook Webhook 配置（可以尚未保存到数据库）
     * @return true = HTTP 200；false = 发送失败
     */
    @Override
    public Boolean testSend(PlanWebhook planWebhook) {
        return webhookNotifyService.testSend(planWebhook);
    }

    /**
     * 删除 Webhook 配置
     * 
     * 逻辑删除，先设置 deletedAt，再调用 deleteById(entity)。
     *
     * @param id 配置ID
     * @return true = 删除成功
     */
    @Override
    public Boolean deleteWebhook(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("Webhook 配置ID不能为空");
        }
        PlanWebhook webhook = planWebhookMapper.selectById(id);
        if (webhook == null) {
            return true;
        }
        webhook.setDeletedAt(new Date());
        return planWebhookMapper.deleteById(webhook) > 0;
    }
}
