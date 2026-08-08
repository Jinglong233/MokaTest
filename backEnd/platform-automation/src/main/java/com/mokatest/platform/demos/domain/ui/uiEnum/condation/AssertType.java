package com.mokatest.platform.demos.domain.ui.uiEnum.condation;

/**
 * @Author JingLong
 * @Description 断言类型
 * @Date 2025/7/25 19:25
 **/
public enum AssertType {
    ELEMENT_EXIST("元素存在"), ELEMENT_NOT_EXIST("元素不存在"), TEXT_EXIST("文本存在"), TEXT_NOT_EXIST("文本不存在"),
    ELEMENT_ARRTRIBUTE("元素属性断言"), PAGE_ARRTRIBUTE("页面属性断言"), CUSTOM("自定义断言");
    private String name;

    AssertType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
