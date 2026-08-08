package com.mokatest.platform.demos.qa.message.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 站内信事件类型
 */
public enum MessageEventType {
    /**
     * BUG 创建
     */
    BUG_CREATED("BUG_CREATED"),

    /**
     * BUG 指派给我
     */
    BUG_ASSIGNED("BUG_ASSIGNED"),

    /**
     * BUG 状态变更
     */
    BUG_STATUS_CHANGED("BUG_STATUS_CHANGED"),

    /**
     * BUG 信息更新
     */
    BUG_UPDATED("BUG_UPDATED"),

    /**
     * BUG 被删除
     */
    BUG_DELETED("BUG_DELETED"),

    /**
     * 需求创建
     */
    REQ_CREATED("REQ_CREATED"),

    /**
     * 需求指派给我
     */
    REQ_ASSIGNED("REQ_ASSIGNED"),

    /**
     * 需求状态变更
     */
    REQ_STATUS_CHANGED("REQ_STATUS_CHANGED"),

    /**
     * 需求信息更新
     */
    REQ_UPDATED("REQ_UPDATED"),

    /**
     * 需求被删除
     */
    REQ_DELETED("REQ_DELETED"),

    /**
     * BUG 评论中 @ 我
     */
    BUG_COMMENT_MENTION("BUG_COMMENT_MENTION"),

    /**
     * 用例创建（预留，暂未连接触发）
     */
    CASE_CREATED("CASE_CREATED"),

    /**
     * 用例状态变更（预留，暂未连接触发）
     */
    CASE_STATUS_CHANGED("CASE_STATUS_CHANGED");

    @EnumValue
    private final String value;

    MessageEventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
