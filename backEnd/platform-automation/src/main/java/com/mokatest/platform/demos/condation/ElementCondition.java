package com.mokatest.platform.demos.condation;

import com.mokatest.platform.demos.domain.ui.uiEnum.condation.AssertRelationship;
import com.mokatest.platform.demos.domain.ui.uiEnum.condation.AssertType;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.element.ElementLocatorProcessor;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.util.VariableReplacer;
import com.microsoft.playwright.Locator;
import lombok.Data;

import java.io.Serializable;

@Data
public class ElementCondition implements TestCondition, Serializable {


    private static final long serialVersionUID = 7828789997160210398L;
    /**
     * 断言类型
     */
    private AssertType assertType;
    private Element element;
    private String expectedValue;
    private String attributeName;
    private int timeout = 5000; // 默认超时5秒

    private AssertRelationship assertRelationship;


    public ElementCondition() {
    }

    @Override
    public AssertResult evaluate(TestExecutionContext context) {
        try {
            String finalValue = expectedValue;

            if (expectedValue != null && !"".equals(expectedValue)) {
                // 统一替换：先变量后函数，二者可混用
                finalValue = VariableReplacer.replace(expectedValue, context.getVariables());
            }
            String assertTip = "";
            AssertResult assertResult = new AssertResult();
            assertResult.setAssertType(assertType.getName());
            switch (assertType) {
                case ELEMENT_EXIST -> {
                    String locatorType = element.getLocatorType().toString().toUpperCase();
                    String locatorValue = element.getLocatorValue();
                    boolean visible = getElementLocator(context, ElementLocatorType.valueOf(locatorType),
                            locatorValue).count() > 0;
                    assertTip = String.format("定位类型：%s ，定位值：%s ，断言结果：%s", locatorType, locatorValue, visible ?
                            "元素存在" : "元素不存在");
                    assertResult.setSuccess(visible);
                    assertResult.setAssertTip(assertTip);
                }
                case ELEMENT_NOT_EXIST -> {
                    String locatorType = element.getLocatorType().toString().toUpperCase();
                    String locatorValue = element.getLocatorValue();
                    boolean visible = getElementLocator(context, ElementLocatorType.valueOf(locatorType),
                            locatorValue).count() > 0;
                    assertTip = String.format("定位类型：%s ，定位值：%s ，断言结果：%s", locatorType, locatorValue, visible ?
                            "元素存在" : "元素不存在");
                    assertResult.setSuccess(!visible);
                    assertResult.setAssertTip(assertTip);
                }
                case ELEMENT_ARRTRIBUTE -> {
                    // 判断元素属性是否是 TEXT，如果是TEXT就说明要获取元素内的文本
                    String attribute = "";
                    if ("TEXT".equals(attributeName)) {
                        attribute = getElementLocator(context,
                                ElementLocatorType.valueOf(element.getLocatorType().toString()),
                                element.getLocatorValue()).innerText();
                    } else {
                        attribute = context.getCurrentFrame().getAttribute(element.getLocatorValue(),
                                attributeName);
                    }
                    assertResult.setAssertRelationship(assertRelationship.name());
                    switch (assertRelationship) {
                        case EQUALS -> {
                            boolean equals = attribute.equals(finalValue);
                            assertTip = String.format("元素属性值：%s，断言关系：%s，期望值：%s,断言结果：%s", attribute, "相等",
                                    finalValue, equals ?
                                            "相等" : "不相等");
                            assertResult.setSuccess(equals);
                            assertResult.setAssertTip(assertTip);
                        }
                        case NOT_EQUALS -> {
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
                        case NOT_CONTAINS -> {
                            boolean contains = attribute.contains(finalValue);
                            assertTip = String.format("元素属性值：%s，断言关系：%s，期望值：%s,断言结果：%s", attribute, "包含",
                                    finalValue, contains ?
                                            "包含" : "不包含");
                            assertResult.setSuccess(!contains);
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
                        case REGULAR -> {
                            boolean matches = finalValue.matches(attribute);
                            assertTip = String.format("元素属性值：%s，断言关系：%s，正则表达式：%s,断言结果：%s", attribute, "正则匹配",
                                    finalValue,
                                    matches ? "匹配成功" : "匹配失败");
                            assertResult.setSuccess(matches);
                            assertResult.setAssertTip(assertTip);
                        }
                        default -> throw new RuntimeException("不支持的断言关系 ：" + assertRelationship);
                    }
                }
                default -> throw new RuntimeException("不支持的断言类型：" + assertType);
            }
            return assertResult;
        } catch (Exception e) {
            // 定位表达式写错（如非法 XPath/CSS）时 Playwright 抛的是底层 evaluate 类型错误，
            // 转成可读提示，避免结果里出现一大段 InjectedScript 堆栈
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("not a node set") || msg.contains("is not a valid") || msg.contains("SyntaxError")) {
                throw new RuntimeException(String.format("元素定位表达式非法（定位类型：%s，定位值：%s），请检查定位值",
                        element != null && element.getLocatorType() != null ? element.getLocatorType() : "未知",
                        element != null ? element.getLocatorValue() : "未知"), e);
            }
            throw new RuntimeException(e);
        }
    }


    /**
     * 根据定位类型返回对应的定位器
     *
     * @param locatorType
     * @param value
     * @return
     */
    private Locator getElementLocator(TestExecutionContext context, ElementLocatorType locatorType, String value) {
        switch (locatorType) {
            case XPATH -> {
                return context.getCurrentFrame().locator(String.format("xpath=%s", value.replaceFirst("^xpath=", "")));
            }
            case TEXT -> {
                return context.getCurrentFrame().getByText(value);
            }
            case PLACEHOLDER -> {
                return context.getCurrentFrame().getByPlaceholder(value);
            }
            case TEST_ID -> {
                return context.getCurrentFrame().getByTestId(value);
            }
            case TITLE -> {
                return context.getCurrentFrame().getByTitle(value);
            }
            case ALT -> {
                return context.getCurrentFrame().getByAltText(value);
            }
            case LABEL -> {
                return context.getCurrentFrame().getByLabel(value);
            }
            case ROLE -> {
                return ElementLocatorProcessor.process(context.getCurrentFrame(), ElementLocatorType.ROLE, value);
            }
            case CSS ->{
                return  context.getCurrentFrame().locator(value);
            }
            default -> throw new RuntimeException("不支持的定位类型 ：" + locatorType);
        }
    }


    @Override
    public String getType() {
        return "element";
    }

}