package com.mokatest.platform.demos.domain.ui.uiEnum.condation;

/**
 * @Author JingLong
 * @Description 断言关系
 * @Date 2025/7/25 17:54
 **/
public enum AssertRelationship {

    EQUALS("相等"),
    NOT_EQUALS("不相等"),
    CONTAINS("包含"),
    NOT_CONTAINS("不包含"),
    GT("大于"),
    LT("小于"),
    GE("大于等于"),
    LE("小于等于"),
    REGULAR("正则匹配");
    private String name;

    AssertRelationship(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
