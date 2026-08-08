package com.mokatest.platform.demos.service.impl;

import com.mokatest.platform.demos.domain.ui.Plan;
import com.mokatest.platform.demos.domain.ui.PlanWebhook;
import com.mokatest.platform.demos.domain.ui.Report;
import com.mokatest.platform.demos.domain.ui.uiEnum.WebhookType;
import com.mokatest.platform.demos.mapper.PlanWebhookMapper;
import com.mokatest.platform.demos.service.WebhookNotifyService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Webhook 通知发送服务实现
 * 
 * 支持钉钉、企业微信、飞书、自定义 URL 四种平台的消息发送。
 * 使用 OkHttp 进行 HTTP 请求，签名使用 JDK 原生 HmacSHA256。
 * 
 * <b>线程安全：</b>OkHttpClient 实例为单例，可安全地在多线程环境中复用。
 * <b>超时配置：</b>连接 5s、读取 10s、写入 10s，避免发送阻塞主流程。
 *
 * @see WebhookNotifyService
 */
@Slf4j
@Service
public class WebhookNotifyServiceImpl implements WebhookNotifyService {

    /**
     * Gson 实例复用，避免重复创建
     */
    private static final Gson GSON = new Gson();

    /**
     * OkHttp 客户端，单例复用
     * 
     * 超时设置较严格，确保发送不会长时间阻塞：
     *   connectTimeout = 5s：建立连接最多等 5 秒
     *   readTimeout = 10s：读取响应最多等 10 秒
     *   writeTimeout = 10s：写入请求最多等 10 秒
     */
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    /**
     * JSON 请求体 MediaType
     */
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    @Resource
    private PlanWebhookMapper planWebhookMapper;

    /**
     * 发送计划执行完成通知
     * 
     * 执行流程：
     * <ol>
     *   检查计划是否开启通知开关
     *   查询该项目下所有 Webhook 配置
     *   过滤出「启用」且「触发时机匹配」的配置
     *   逐个发送，每个配置独立 try-catch，互不影响
     * </ol>
     */
    @Override
    public void send(Plan plan, Report report) {
        // 步骤 1：检查计划是否开启了通知开关
        // plan.webhook_enabled 在数据库中是 tinyint(1)，映射为 Integer（0=关，1=开）
        if (plan == null || !Integer.valueOf(1).equals(plan.getWebhookEnabled())) {
            log.debug("计划 [{}] 未开启 Webhook 通知，跳过发送", plan != null ? plan.getId() : "null");
            return;
        }

        // 步骤 2：查询该项目下的所有 Webhook 配置
        List<PlanWebhook> webhooks = planWebhookMapper.selectByProjectId(plan.getProjectId());
        if (webhooks == null || webhooks.isEmpty()) {
            log.debug("项目 [{}] 下无 Webhook 配置，跳过发送", plan.getProjectId());
            return;
        }

        // 步骤 2.5：按 plan.webhook_ids 过滤，只发送计划关联的配置
        // webhook_ids 为逗号分隔字符串，如 "1,2,3"；为空或 null 时发送所有启用的配置（向下兼容）
        if (plan.getWebhookIds() != null && !plan.getWebhookIds().trim().isEmpty()) {
            java.util.Set<Integer> selectedIds = java.util.Arrays.stream(plan.getWebhookIds().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Integer::parseInt)
                    .collect(java.util.stream.Collectors.toSet());
            if (!selectedIds.isEmpty()) {
                webhooks = webhooks.stream()
                        .filter(h -> selectedIds.contains(h.getId()))
                        .collect(java.util.stream.Collectors.toList());
                log.info("计划 [{}] 关联了 {} 个 Webhook 配置，ID={}",
                        plan.getId(), webhooks.size(), plan.getWebhookIds());
            }
        }

        // 步骤 3：根据报告结果判断执行类型
        // 规则：步骤失败数 > 0 视为 FAILURE，否则为 SUCCESS
        // 断言失败不单独判定，统一归入步骤结果
        String resultType = (report.getStepErrorNumber() != null && report.getStepErrorNumber() > 0)
                ? "FAILURE" : "SUCCESS";
        log.info("计划 [{}] 执行完成，结果类型：{}，准备发送 Webhook 通知", plan.getId(), resultType);

        // 步骤 4：逐个发送通知
        for (PlanWebhook hook : webhooks) {
            // 4.1 跳过已禁用的配置
            if (!Boolean.TRUE.equals(hook.getEnabled())) {
                log.debug("Webhook 配置 [{}] 已禁用，跳过", hook.getId());
                continue;
            }

            // 4.2 跳过不匹配触发时机的配置
            // notifyOn 为逗号分隔字符串，如 "SUCCESS,FAILURE"
            if (hook.getNotifyOn() == null || !hook.getNotifyOn().contains(resultType)) {
                log.debug("Webhook 配置 [{}] 触发时机 [{}] 不匹配结果 [{}]，跳过",
                        hook.getId(), hook.getNotifyOn(), resultType);
                continue;
            }

            // 4.3 发送（独立 try-catch，单个失败不影响其他配置）
            try {
                doSend(hook, plan, report);
                log.info("Webhook 配置 [{}] 平台 [{}] 发送成功", hook.getId(), hook.getType());
            } catch (Exception e) {
                log.error("Webhook 配置 [{}] 平台 [{}] 发送失败，URL：{}",
                        hook.getId(), hook.getType(), hook.getUrl(), e);
            }
        }
    }

    /**
     * 测试发送
     * 
     * 构造一条测试消息，不依赖 Report 数据，仅验证 Webhook URL 和密钥是否正确。
     * 用于前端「测试发送」按钮。
     */
    @Override
    public Boolean testSend(PlanWebhook planWebhook) {
        if (planWebhook == null || planWebhook.getUrl() == null || planWebhook.getUrl().trim().isEmpty()) {
            log.warn("测试发送失败：Webhook 配置或 URL 为空");
            return false;
        }

        try {
            // 构造测试消息内容
            String messageText = "**MokaTest Webhook 测试**\n"
                    + "这是一条测试消息，如果您收到了，说明 Webhook 配置正确。\n"
                    + "配置名称：" + (planWebhook.getName() != null ? planWebhook.getName() : "未命名") + "\n"
                    + "平台类型：" + planWebhook.getType();

            // 根据平台类型构造请求体
            String requestBody = buildRequestBody(planWebhook, "测试计划", messageText, true);

            // 计算签名并构造完整 URL
            String finalUrl = buildSignedUrl(planWebhook);

            // 发送 HTTP POST 请求
            Request request = new Request.Builder()
                    .url(finalUrl)
                    .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                    .build();

            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "empty";
                log.info("Webhook 测试发送 [{}] 响应：HTTP {}，body={}", planWebhook.getType(), response.code(), body);
                boolean success = response.isSuccessful();
                if (!success) {
                    log.warn("测试发送响应异常，code={}，body={}", response.code(), body);
                }
                return success;
            }
        } catch (Exception e) {
            log.error("测试发送异常", e);
            return false;
        }
    }

    /**
     * 执行实际发送
     *
     * @param hook   Webhook 配置
     * @param plan   计划对象
     * @param report 报告对象
     */
    private void doSend(PlanWebhook hook, Plan plan, Report report) throws Exception {
        // 构造消息标题和内容
        String title = buildMessageTitle(plan, report);
        String content = buildMessageContent(plan, report);

        // 根据平台类型构造 JSON 请求体
        String requestBody = buildRequestBody(hook, title, content, false);

        // 计算签名（如配置了 secret）并构造完整 URL
        String finalUrl = buildSignedUrl(hook);

        // 发送 HTTP POST
        log.info("Webhook 请求 [{}] URL={}，Body={}", hook.getType(), finalUrl, requestBody);
        Request request = new Request.Builder()
                .url(finalUrl)
                .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "empty";
            log.info("Webhook 平台 [{}] 响应：HTTP {}，body={}", hook.getType(), response.code(), body);
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP " + response.code() + ", body=" + body);
            }
        }
    }

    /**
     * 构造消息标题
     */
    private String buildMessageTitle(Plan plan, Report report) {
        boolean hasError = report.getStepErrorNumber() != null && report.getStepErrorNumber() > 0;
        String emoji = hasError ? "❌" : "✅";
        return emoji + " MokaTest 执行通知";
    }

    /**
     * 构造消息正文
     * 
     * 各平台通用文本内容，Markdown 格式。
     */
    private String buildMessageContent(Plan plan, Report report) {
        boolean hasError = report.getStepErrorNumber() != null && report.getStepErrorNumber() > 0;
        String resultText = hasError ? "失败" : "成功";
        String resultEmoji = hasError ? "❌" : "✅";

        // 格式化耗时：毫秒转秒，保留 1 位小数
        String durationStr = "—";
        if (report.getExecutionDuration() != null) {
            double seconds = report.getExecutionDuration() / 1000.0;
            durationStr = String.format("%.1f 秒", seconds);
        }

        // 格式化时间
        String endTimeStr = "—";
        if (report.getEndTime() != null) {
            endTimeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(report.getEndTime());
        }

        return "**计划名称：**" + (plan.getPlanName() != null ? plan.getPlanName() : "—") + "\n"
                + "**执行结果：**" + resultEmoji + " " + resultText + "\n"
                + "**场景数：**" + (report.getSceneNumber() != null ? report.getSceneNumber() : 0) + " 个\n"
                + "**步骤统计：**成功 " + (report.getStepSuccessNumber() != null ? report.getStepSuccessNumber() : 0)
                + "，失败 " + (report.getStepErrorNumber() != null ? report.getStepErrorNumber() : 0)
                + "，跳过 " + (report.getStepSkipNumber() != null ? report.getStepSkipNumber() : 0) + "\n"
                + "**断言统计：**成功 " + (report.getAssertSuccessNumber() != null ? report.getAssertSuccessNumber() : 0)
                + "，失败 " + (report.getAssertErrorNumber() != null ? report.getAssertErrorNumber() : 0)
                + "，跳过 " + (report.getAssertSkipNumber() != null ? report.getAssertSkipNumber() : 0) + "\n"
                + "**执行耗时：**" + durationStr + "\n"
                + "**完成时间：**" + endTimeStr + "\n"
                + "**报告ID：**" + (report.getId() != null ? report.getId() : "—");
    }

    /**
     * 根据平台类型构造请求体 JSON
     *
     * @param hook      Webhook 配置
     * @param title     消息标题
     * @param content   消息内容（Markdown 格式）
     * @param isTest    是否为测试消息
     * @return JSON 字符串
     */
    private String buildRequestBody(PlanWebhook hook, String title, String content, boolean isTest) {
        WebhookType type = WebhookType.valueOf(hook.getType());

        switch (type) {
            case DINGTALK:
                return buildDingTalkBody(hook, title, content, isTest);
            case WECHAT:
                return buildWechatBody(hook, title, content, isTest);
            case FEISHU:
                return buildFeishuBody(hook, title, content, isTest);
            case CUSTOM:
                return buildCustomBody(hook, title, content, isTest);
            default:
                throw new IllegalArgumentException("不支持的 Webhook 类型：" + hook.getType());
        }
    }

    /**
     * 构造钉钉消息体
     * 
     * 使用 markdown 类型，支持 @手机号。
     */
    private String buildDingTalkBody(PlanWebhook hook, String title, String content, boolean isTest) {
        JsonObject markdown = new JsonObject();
        markdown.addProperty("title", title);
        markdown.addProperty("text", "#### " + title + "\n" + content);

        // 构造 @ 信息
        JsonObject at = new JsonObject();
        if (hook.getAtMobiles() != null && !hook.getAtMobiles().isEmpty()) {
            // 将逗号分隔的手机号转为数组
            String[] mobiles = hook.getAtMobiles().split(",");
            at.add("atMobiles", GSON.toJsonTree(mobiles));
            at.addProperty("isAtAll", false);
            // 在内容末尾追加 @文本，确保钉钉能正确高亮
            StringBuilder atText = new StringBuilder();
            for (String mobile : mobiles) {
                atText.append("@").append(mobile.trim()).append(" ");
            }
            markdown.addProperty("text", markdown.get("text").getAsString() + "\n\n" + atText.toString().trim());
        } else {
            at.addProperty("isAtAll", false);
        }

        JsonObject body = new JsonObject();
        body.addProperty("msgtype", "markdown");
        body.add("markdown", markdown);
        body.add("at", at);

        return GSON.toJson(body);
    }

    /**
     * 构造企业微信消息体
     * 
     * 使用 markdown 类型，企微 markdown 语法与标准 Markdown 略有不同，使用 ">" 引用格式。
     */
    private String buildWechatBody(PlanWebhook hook, String title, String content, boolean isTest) {
        // 将内容中的 **bold** 转为企微支持的格式，并拼接成 markdown 引用块
        StringBuilder markdownContent = new StringBuilder();
        markdownContent.append(title).append("\n");

        // 逐行处理，转换为企微 markdown 格式
        String[] lines = content.split("\n");
        for (String line : lines) {
            markdownContent.append(">").append(line).append("\n");
        }

        // 追加 @信息（企微使用 mentioned_mobile_list）
        JsonObject markdown = new JsonObject();
        markdown.addProperty("content", markdownContent.toString().trim());

        JsonObject body = new JsonObject();
        body.addProperty("msgtype", "markdown");
        body.add("markdown", markdown);

        // 企微支持 mentioned_mobile_list
        if (hook.getAtMobiles() != null && !hook.getAtMobiles().isEmpty()) {
            String[] mobiles = hook.getAtMobiles().split(",");
            body.add("mentioned_mobile_list", GSON.toJsonTree(mobiles));
        }

        return GSON.toJson(body);
    }

    /**
     * 构造飞书消息体
     * 
     * 使用 text 类型（最通用，无需卡片模板），如后续需要卡片样式可升级。
     */
    private String buildFeishuBody(PlanWebhook hook, String title, String content, boolean isTest) {
        JsonObject text = new JsonObject();
        // 飞书 text 消息不需要 Markdown 解析，将 ** 去掉
        String plainText = (title + "\n" + content).replace("**", "").replace("❌", "[失败]").replace("✅", "[成功]");
        text.addProperty("text", plainText);

        JsonObject body = new JsonObject();
        body.addProperty("msg_type", "text");
        body.add("content", text);

        return GSON.toJson(body);
    }

    /**
     * 构造自定义 Webhook 消息体
     * 
     * 发送标准 JSON Payload，方便用户自行解析。
     */
    private String buildCustomBody(PlanWebhook hook, String title, String content, boolean isTest) {
        JsonObject body = new JsonObject();
        body.addProperty("event", isTest ? "TEST" : "PLAN_EXECUTION_FINISHED");
        body.addProperty("title", title);
        body.addProperty("content", content);
        body.addProperty("timestamp", System.currentTimeMillis());

        // 测试消息不附带报告数据
        if (!isTest) {
            // 从 content 中解析出的报告信息也可以作为独立字段提供，方便接收方直接取用
            body.addProperty("isTest", false);
        } else {
            body.addProperty("isTest", true);
        }

        return GSON.toJson(body);
    }

    /**
     * 计算签名并构造完整 URL
     * 
     * 钉钉/企微/飞书均使用 HmacSHA256 加签算法，但时间戳单位不同：
     *   钉钉/企微：毫秒级时间戳（13位）
     *   飞书：秒级时间戳（10位）
     * <pre>
     *   stringToSign = timestamp + "\n" + secret
     *   sign = Base64(HmacSHA256(stringToSign, secret))
     *   url 追加 &timestamp={timestamp}&sign={sign}
     * </pre>
     * 
     * 若未配置 secret（空字符串或 null），直接返回原始 URL，不追加签名参数。
     *
     * @param hook Webhook 配置
     * @return 带签名参数的完整 URL
     */
    private String buildSignedUrl(PlanWebhook hook) throws Exception {
        String url = hook.getUrl();
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("Webhook URL 不能为空");
        }

        // 未配置密钥时直接返回原始 URL
        if (hook.getSecret() == null || hook.getSecret().trim().isEmpty()) {
            return url;
        }

        // 不同平台时间戳单位不同：飞书用秒级，钉钉/企微用毫秒级
        long timestamp;
        if (WebhookType.FEISHU.name().equals(hook.getType())) {
            timestamp = System.currentTimeMillis() / 1000;
        } else {
            timestamp = System.currentTimeMillis();
        }
        log.info("Webhook 签名计算，平台=[{}]，timestamp=[{}]，secret长度=[{}]",
                hook.getType(), timestamp, hook.getSecret() != null ? hook.getSecret().length() : 0);

        // HmacSHA256 签名 —— 不同平台签名算法不同：
        // 钉钉/企微：key = secret，message = timestamp + "\n" + secret
        // 飞书：    key = timestamp + "\n" + secret，message = ""（空消息）
        Mac mac = Mac.getInstance("HmacSHA256");
        byte[] signData;
        if (WebhookType.FEISHU.name().equals(hook.getType())) {
            String stringToSign = timestamp + "\n" + hook.getSecret();
            mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            signData = mac.doFinal(new byte[0]); // message 为空
        } else {
            String stringToSign = timestamp + "\n" + hook.getSecret();
            mac.init(new SecretKeySpec(hook.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        }

        // Base64 编码
        String sign = Base64.getEncoder().encodeToString(signData);

        // 钉钉/企微需要对 sign 做 URL 编码（官方文档要求）
        // 飞书不需要 URL 编码，直接拼接原始 Base64 字符串
        if (!WebhookType.FEISHU.name().equals(hook.getType())) {
            sign = URLEncoder.encode(sign, StandardCharsets.UTF_8.name());
        }

        // 拼接参数（判断 URL 是否已有查询参数，决定用 ? 还是 &）
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "timestamp=" + timestamp + "&sign=" + sign;
    }
}
