package com.mokatest.platform.demos.domain.ui.uiEnum;

/**
 * Webhook 通知平台类型枚举
 * 
 * 支持的第三方通知平台：钉钉、企业微信、飞书、自定义 URL。
 * 新增平台时在此枚举中扩展，并在 {@link com.mokatest.platform.demos.service.impl.WebhookNotifyServiceImpl}
 * 中实现对应的消息构造与发送逻辑。
 */
public enum WebhookType {

    /**
     * 钉钉群机器人
     * 文档：https://open.dingtalk.com/document/orgapp/robots-send-group-messages
     */
    DINGTALK,

    /**
     * 企业微信群机器人
     * 文档：https://developer.work.weixin.qq.com/document/path/91770
     */
    WECHAT,

    /**
     * 飞书群机器人
     * 文档：https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot
     */
    FEISHU,

    /**
     * 自定义 HTTP Webhook
     * 用户自行提供接收地址，POST 标准 JSON Payload
     */
    CUSTOM
}
