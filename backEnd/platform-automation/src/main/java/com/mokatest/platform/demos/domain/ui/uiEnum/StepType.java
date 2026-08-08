package com.mokatest.platform.demos.domain.ui.uiEnum;
// 步骤类型

public enum StepType {
    // 点击
    CLICK,
    // 悬停
    HOVER,

    // 断言
    ASSERT,

    // 关闭页面
    CLOSE_PAGE,


    // 打开页面
    OPEN_PAGE,

    // 前进
    FORWARD,

    // 后退
    BACK,

    // 刷新
    REFRESH,

    // 拖拽元素
    DRAG,

    // 关联提取
    EXTRACT,


    // 键盘操作
    KEYBOARD,

    // 切换tab
    SWITCH_TAB,

    // 等待
    WAIT,

    // if判断
    IF,

    // while循环
    WHILE,

    // for循环
    FOR,

    // iframe操作
    IFRAME,

    // 对话框操作
    DIALOG,

    // 文件上传
    FILE_UPLOAD,

    // 元素DOM操作
    ELEMENT_DOM_OPERATION,

    // API请求
    API_REQUEST,

    // SQL查询
    SQL,

    // JS脚本
    SCRIPT

}