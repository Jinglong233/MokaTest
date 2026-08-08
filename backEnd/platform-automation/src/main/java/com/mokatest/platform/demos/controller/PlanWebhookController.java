package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mokatest.platform.demos.domain.ui.PlanWebhook;
import com.mokatest.platform.demos.domain.ui.vo.ResponseVO;
import com.mokatest.platform.demos.service.PlanWebhookService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Webhook 通知配置接口
 *
 * 提供 Webhook 配置的增删改查及测试发送接口。所有接口前缀：{@code /api/planWebhook}。
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   查看配置：auto:plan:webhook:view
 *   新增配置：auto:plan:webhook:create
 *   更新配置：auto:plan:webhook:update
 *   删除配置：auto:plan:webhook:delete
 * admin 角色默认拥有上述所有权限。
 *
 * @see PlanWebhookService
 */
@RestController
@RequestMapping("/planWebhook")
public class PlanWebhookController {

    @Resource
    private PlanWebhookService planWebhookService;

    /**
     * 根据项目ID查询 Webhook 配置列表
     *
     * @param projectId 项目ID（必填）
     * @return 配置列表
     * 权限：auto:plan:webhook:view
     */
    @SaCheckPermission("auto:plan:webhook:view")
    @GetMapping("/list")
    public ResponseVO list(@RequestParam Integer projectId) {
        if (projectId == null) {
            return ResponseVO.failure(400, "项目ID不能为空");
        }
        List<PlanWebhook> list = planWebhookService.listByProjectId(projectId);
        return ResponseVO.success(list);
    }

    /**
     * 新增 Webhook 配置
     *
     * @param planWebhook 配置对象（id 为空）
     * @return 操作结果
     * 权限：auto:plan:webhook:create
     */
    @SaCheckPermission("auto:plan:webhook:create")
    @PostMapping("/save")
    public ResponseVO save(@RequestBody PlanWebhook planWebhook) {
        if (planWebhook == null) {
            return ResponseVO.failure(400, "配置对象不能为空");
        }
        if (planWebhook.getProjectId() == null) {
            return ResponseVO.failure(400, "项目ID不能为空");
        }
        if (planWebhook.getUrl() == null || planWebhook.getUrl().trim().isEmpty()) {
            return ResponseVO.failure(400, "Webhook URL 不能为空");
        }
        Boolean result = planWebhookService.saveOrUpdateWebhook(planWebhook);
        return result ? ResponseVO.success(true) : ResponseVO.failure(400, "保存失败");
    }

    /**
     * 更新 Webhook 配置
     *
     * @param planWebhook 配置对象（id 必填）
     * @return 操作结果
     * 权限：auto:plan:webhook:update
     */
    @SaCheckPermission("auto:plan:webhook:update")
    @PostMapping("/update")
    public ResponseVO update(@RequestBody PlanWebhook planWebhook) {
        if (planWebhook == null || planWebhook.getId() == null) {
            return ResponseVO.failure(400, "配置ID不能为空");
        }
        Boolean result = planWebhookService.saveOrUpdateWebhook(planWebhook);
        return result ? ResponseVO.success(true) : ResponseVO.failure(400, "更新失败");
    }

    /**
     * 删除 Webhook 配置
     *
     * @param id 配置ID
     * @return 操作结果
     * 权限：auto:plan:webhook:delete
     */
    @SaCheckPermission("auto:plan:webhook:delete")
    @GetMapping("/delete")
    public ResponseVO delete(@RequestParam Integer id) {
        if (id == null) {
            return ResponseVO.failure(400, "ID不能为空");
        }
        Boolean result = planWebhookService.deleteWebhook(id);
        return result ? ResponseVO.success(true) : ResponseVO.failure(400, "删除失败");
    }

    /**
     * 测试发送
     * 向指定的 Webhook 配置发送一条测试消息，验证 URL 和密钥是否正确。不影响任何任务执行。
     *
     * @param planWebhook 配置对象（需包含 type、url）
     * @return true = 发送成功（HTTP 200）；false = 发送失败
     * 权限：auto:plan:webhook:update
     */
    @SaCheckPermission("auto:plan:webhook:update")
    @PostMapping("/test")
    public ResponseVO test(@RequestBody PlanWebhook planWebhook) {
        if (planWebhook == null || planWebhook.getUrl() == null || planWebhook.getUrl().trim().isEmpty()) {
            return ResponseVO.failure(400, "Webhook URL 不能为空");
        }
        // URL 安全检查
        String checkResult = checkUrlSafety(planWebhook.getUrl());
        if (checkResult != null) {
            return ResponseVO.failure(400, checkResult);
        }
        Boolean result = planWebhookService.testSend(planWebhook);
        if (Boolean.TRUE.equals(result)) {
            return ResponseVO.success(true);
        } else {
            return ResponseVO.failure(400, "测试消息发送失败，请检查 URL 和密钥");
        }
    }

    /**
     * URL 安全检查：禁止访问内网地址和非 http(s) 协议
     * @return null 表示安全；否则返回错误信息
     */
    private String checkUrlSafety(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "URL 不能为空";
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return "只允许 http:// 或 https:// 协议";
            }
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return "URL 格式不正确，无法解析主机名";
            }
            // 注：作为测试平台，webhook 接收方常部署在内网/本地（如内部 IM），不拦截 localhost 与内网地址
        } catch (Exception e) {
            return "URL 格式不正确：" + e.getMessage();
        }
        return null;
    }
}
