package com.mokatest.platform.demos.condation;

import com.mokatest.platform.demos.domain.ui.uiEnum.condation.AssertRelationship;
import com.mokatest.platform.demos.domain.ui.uiEnum.condation.AssertType;
import com.mokatest.platform.demos.domain.ui.uiEnum.page.PageAttribute;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.util.VariableReplacer;
import com.microsoft.playwright.Page;
import lombok.Data;

import java.io.Serializable;

/**
 * @Author JingLong
 * @Description 页面条件
 * @Date 2025/7/29 17:13
 **/
@Data
public class PageCondition implements TestCondition, Serializable {


    private static final long serialVersionUID = 5462916952676030912L;
    /**
     * 断言类型
     */
    private AssertType assertType;

    /**
     * 断言关系
     */
    private AssertRelationship assertRelationship;

    /**
     * 页面属性
     */
    private String attributeName;

    /**
     * 期望值
     */
    private String expectedValue;


    @Override
    public AssertResult evaluate(TestExecutionContext context) {
        try {
            String finalValue = expectedValue;

            String attribute = getAttrByName(context.getCurrentPage());
            if (expectedValue != null && !"".equals(expectedValue)) {
                // 统一替换：先变量后函数，二者可混用
                finalValue = VariableReplacer.replace(expectedValue, context.getVariables());
            }
            String assertTip = "";
            AssertResult assertResult = new AssertResult();
            assertResult.setAssertType(assertType.getName());
            assertResult.setAssertRelationship(assertRelationship.getName());
            switch (assertRelationship) {
                case EQUALS -> {
                    boolean equals = attribute.equals(finalValue);
                    assertTip = String.format("元素属性值：%s，断言关系：%s，期望值：%s,断言结果：%s", attribute, "相等",
                            finalValue, equals ?
                                    "相等" : "不相等");
                    assertResult.setSuccess(equals);
                    assertResult.setAssertTip(assertTip);
                }
                case NOT_CONTAINS -> {
                    boolean equals = attribute.equals(finalValue);
                    assertTip = String.format("元素属性值：%s，断言关系：%s，期望值：%s,断言结果：%s", attribute, "不相等",
                            finalValue, equals ?
                                    "相等" : "不相等");
                    assertResult.setSuccess(!equals);
                    assertResult.setAssertTip(assertTip);
                }
                case CONTAINS -> {
                    boolean contains = attribute.contains(finalValue);
                    assertTip = String.format("元素属性值：%s，断言关系：%s，期望值：%s,断言结果：%s", attribute, "包含",
                            finalValue, contains ?
                                    "包含" : "不包含");
                    assertResult.setSuccess(contains);
                    assertResult.setAssertTip(assertTip);
                }
                case NOT_EQUALS -> {
                    boolean contains = attribute.contains(finalValue);
                    assertTip = String.format("元素属性值：%s，断言关系：%s，期望值：%s,断言结果：%s", attribute, "包含",
                            finalValue, contains ?
                                    "包含" : "不包含");
                    assertResult.setSuccess(!contains);
                    assertResult.setAssertTip(assertTip);
                }
                case GE -> {
                    boolean b = Double.parseDouble(attribute) >= Double.parseDouble(finalValue);
                    assertTip = String.format("元素属性值：%s，断言关系：%s，期望值：%s,断言结果：%s", attribute, "大于等于",
                            finalValue,
                            b ? "大于等于" : "!大于等于");
                    assertResult.setSuccess(b);
                    assertResult.setAssertTip(assertTip);
                }
                case LE -> {
                    boolean b = Double.parseDouble(attribute) <= Double.parseDouble(finalValue);
                    assertTip = String.format("元素属性值：%s，断言关系：%s，期望值：%s,断言结果：%s", attribute, "小于等于",
                            finalValue,
                            b ? "小于等于" : "!小于等于");
                    assertResult.setSuccess(b);
                    assertResult.setAssertTip(assertTip);
                }
                case GT -> {
                    boolean b = Double.parseDouble(attribute) > Double.parseDouble(finalValue);
                    assertTip = String.format("元素属性值：%s，断言关系：%s，期望值：%s,断言结果：%s", attribute, "大于",
                            finalValue,
                            b ? "大于" : "不大于");
                    assertResult.setSuccess(b);
                    assertResult.setAssertTip(assertTip);
                }
                case LT -> {
                    boolean b = Double.parseDouble(attribute) < Double.parseDouble(finalValue);
                    assertTip = String.format("元素属性值：%s，断言关系：%s，期望值：%s,断言结果：%s", attribute, "小于",
                            finalValue,
                            b ? "小于" : "不大于");
                    assertResult.setSuccess(b);
                    assertResult.setAssertTip(assertTip);

                }
                case REGULAR -> {
                    boolean matches = finalValue.matches(attribute);
                    assertTip = String.format("元素属性值：%s，断言关系：%s，正则表达式：%s,断言结果：%s", attribute, "正则匹配",
                            finalValue,
                            matches ? "匹配成功" : "匹配失败");
                    assertResult.setSuccess(matches);
                    assertResult.setAssertTip(assertTip);
                }
                default -> throw new RuntimeException("不支持的断言关系：" + assertRelationship);
            }
            return assertResult;
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getType() {
        return "page";
    }


    private String getAttrByName(Page page) {
        PageAttribute pageAttribute = PageAttribute.valueOf(attributeName);
        switch (pageAttribute) {
            case PAGE_TITLE -> {
                return page.title();
            }
            case PAGE_URL -> {
                return page.url();
            }
            default -> {
                throw new RuntimeException("不支持的页面属性：" + attributeName);
            }
        }
    }
}
