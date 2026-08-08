package com.mokatest.platform.demos.domain.ui;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Webhook 通知配置
 */
@Data
public class WebhookConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 通知类型：DINGTALK / WECHAT / FEISHU / CUSTOM
     */
    private String type;

    /**
     * Webhook URL
     */
    private String url;

    /**
     * 签名密钥（钉钉/企微/飞书加签用）
     */
    private String secret;

    /**
     * 通知触发时机：SUCCESS / FAILURE（默认都通知）
     */
    private List<String> notifyOn;

    /**
     * @指定人手机号（钉钉/企微用）
     */
    private List<String> atMobiles;

    public WebhookConfig() {
        this.enabled = false;
    }
}
