package com.mokatest.platform.demos.operationlog.enums;

import lombok.Getter;

/**
 * 操作类型枚举（label 为前端展示文案，选项接口直接下发，避免前端重复维护翻译）
 */
@Getter
public enum OperateType {
    CREATE("创建"),
    UPDATE("更新"),
    DELETE("删除"),
    EXECUTE("执行"),
    TRANSITION("流转"),
    LOGIN("登录"),
    LOGOUT("登出"),
    IMPORT("导入"),
    EXPORT("导出"),
    BIND("绑定"),
    UNBIND("解绑"),
    SORT("排序"),
    BATCH_DELETE("批量删除");

    private final String label;

    OperateType(String label) {
        this.label = label;
    }
}
