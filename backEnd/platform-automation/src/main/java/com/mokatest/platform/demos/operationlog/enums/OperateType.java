package com.mokatest.platform.demos.operationlog.enums;

/**
 * 操作类型枚举
 */
public enum OperateType {
    CREATE,       // 创建
    UPDATE,       // 更新
    DELETE,       // 删除
    EXECUTE,      // 执行（运行测试、触发计划等）
    TRANSITION,   // 状态流转
    LOGIN,        // 登录
    LOGOUT,       // 登出
    IMPORT,       // 导入
    EXPORT,       // 导出
    BIND,         // 绑定/关联
    UNBIND,       // 解绑
    SORT,         // 排序
    BATCH_DELETE  // 批量删除
}
