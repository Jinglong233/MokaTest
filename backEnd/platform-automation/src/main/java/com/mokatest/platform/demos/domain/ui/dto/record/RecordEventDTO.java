package com.mokatest.platform.demos.domain.ui.dto.record;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 单个录制事件
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordEventDTO {

    /**
     * 事件序号
     */
    private Integer seq;

    /**
     * 事件动作
     */
    private String action;

    /**
     * 目标 URL（OPEN_URL / NAVIGATE）
     */
    private String url;

    /**
     * 跳转前 URL（NAVIGATE）
     */
    private String from;

    /**
     * 操作元素
     */
    private RecordElementDTO element;

    /**
     * 输入/选择值
     */
    private String value;

    /**
     * 下拉框选中文本
     */
    private String valueText;

    /**
     * 按键（当前只有 ENTER）
     */
    private String key;

    /**
     * 是否为密码框输入
     */
    private Boolean isPassword;

    /**
     * iframe 切换类型（IFRAME_ENTER）
     */
    private String switchIframeType;

    /**
     * iframe 名称（IFRAME_ENTER）
     */
    private String iframeName;

    /**
     * iframe id（IFRAME_ENTER）
     */
    private String iframeId;

    /**
     * iframe 索引（IFRAME_ENTER），插件按 Playwright frames() 顺序 1-based
     */
    private Integer iframeIndex;

    /**
     * 关闭页面模式（CLOSE_PAGE）
     */
    private String closePageMode;

    /**
     * 自定义关闭索引（CLOSE_PAGE），0-based
     */
    private Integer customIndex;

    /**
     * 事件发生时所在 frame 的 URL
     */
    private String frameUrl;

}
