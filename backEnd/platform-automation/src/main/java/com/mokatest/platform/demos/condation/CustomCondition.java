package com.mokatest.platform.demos.condation;

import com.mokatest.platform.demos.domain.ui.uiEnum.condation.AssertRelationship;
import com.mokatest.platform.demos.domain.ui.uiEnum.condation.AssertType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.util.VariableReplacer;
import lombok.Data;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.Serializable;

import static com.mokatest.platform.demos.domain.ui.uiEnum.condation.AssertType.CUSTOM;

/**
 * @Author JingLong
 * @Description
 * @Date 2026/1/4 17:44
 **/
@Data
public class CustomCondition implements TestCondition, Serializable {


    private static final long serialVersionUID = -1766836305566442861L;

    /**
     * 断言类型
     */
    private AssertType assertType;

    /**
     * 断言文本
     */
    private String assertText;

    private AssertRelationship assertRelationship;

    /**
     * 预期文本
     */
    private String expectedValue;


    @Override
    public AssertResult evaluate(TestExecutionContext context) {
        String expectedValue1 = expectedValue;
        String assertText1 = assertText;

        // 期望值替换（先变量后函数，二者可混用）
        if (expectedValue != null && !"".equals(expectedValue)) {
            expectedValue1 = VariableReplacer.replace(expectedValue, context.getVariables());
        }

        // 断言值替换（先变量后函数，二者可混用）
        if (assertText != null && !"".equals(assertText)) {
            assertText1 = VariableReplacer.replace(assertText, context.getVariables());
        }
        String assertTip = "";
        AssertResult assertResult = new AssertResult();
        assertResult.setAssertType(assertType.getName());
        assertResult.setAssertRelationship(assertRelationship.name());
        // 判断断言类型
        if (assertType.equals(CUSTOM)) {
            switch (assertRelationship) {
                case EQUALS -> {
                    boolean equals = assertText1.equals(expectedValue1);
                    assertTip = String.format("断言值：%s,断言关系：%s，期望值：%s,断言结果：%s", assertText1, "相等",
                            expectedValue1, equals ? "相等" : "不相等");
                    assertResult.setSuccess(equals);
                    assertResult.setAssertTip(assertTip);
                }
                case NOT_EQUALS -> {
                    boolean equals = assertText1.equals(expectedValue1);
                    assertTip = String.format("断言值：%s,断言关系：%s，期望值：%s,断言结果：%s", assertText1, "不相等",
                            expectedValue1, equals ? "相等" : "不相等");
                    assertResult.setSuccess(!equals);
                    assertResult.setAssertTip(assertTip);
                }
                case CONTAINS -> {
                    boolean contains = assertText1.contains(expectedValue1);
                    assertTip = String.format("断言值：%s,断言关系：%s，期望值：%s,断言结果：%s", assertText1, "包含",
                            expectedValue1, contains ? "包含" : "不包含");
                    assertResult.setSuccess(contains);
                    assertResult.setAssertTip(assertTip);
                }
                case NOT_CONTAINS -> {
                    boolean contains = assertText1.contains(expectedValue1);
                    assertTip = String.format("断言值：%s,断言关系：%s，期望值：%s,断言结果：%s", assertText1, "不包含",
                            expectedValue1, contains ? "包含" : "不包含");
                    assertResult.setSuccess(!contains);
                    assertResult.setAssertTip(assertTip);
                }
                case GT -> {
                    // 判断是否为数字
                    if (NumberUtils.isNumber(expectedValue1) && NumberUtils.isNumber(assertText1)) {
                        boolean b = Double.parseDouble(assertText1) > Double.parseDouble(expectedValue1);
                        assertTip = String.format("断言值：%s，断言关系：%s，期望值：%s,断言结果：%s", assertText1, "大于",
                                expectedValue1,
                                b ? "大于" : "!大于");
                        assertResult.setSuccess(b);
                        assertResult.setAssertTip(assertTip);
                    } else {
                        assertResult.setSuccess(false);
                        assertResult.setAssertTip("请检查 期望值、断言值 的数据格式（必须为数字）");
                    }
                }
                case LT -> {
                    // 判断是否为数字
                    if (NumberUtils.isNumber(expectedValue1) && NumberUtils.isNumber(assertText1)) {
                        boolean b = Double.parseDouble(assertText1) < Double.parseDouble(expectedValue1);
                        assertTip = String.format("断言值：%s，断言关系：%s，期望值：%s,断言结果：%s", assertText1, "小于",
                                expectedValue1, b ? "小于" : "!小于");
                        assertResult.setSuccess(b);
                        assertResult.setAssertTip(assertTip);
                    } else {
                        assertResult.setSuccess(false);
                        assertResult.setAssertTip("请检查 期望值、断言值 的数据格式（必须为数字）");
                    }
                }
                case GE -> {
                    // 判断是否为数字
                    if (NumberUtils.isNumber(expectedValue1) && NumberUtils.isNumber(assertText1)) {
                        boolean b = Double.parseDouble(assertText1) >= Double.parseDouble(expectedValue1);
                        assertTip = String.format("断言值：%s，断言关系：%s，期望值：%s,断言结果：%s", assertText1, "大于等于",
                                expectedValue1, b ? "大于等于" : "!大于等于");
                        assertResult.setSuccess(b);
                        assertResult.setAssertTip(assertTip);
                    } else {
                        assertResult.setSuccess(false);
                        assertResult.setAssertTip("请检查 期望值、断言值 的数据格式（必须为数字）");
                    }
                }
                case LE -> {
                    // 判断是否为数字
                    if (NumberUtils.isNumber(expectedValue1) && NumberUtils.isNumber(assertText1)) {
                        boolean b = Double.parseDouble(assertText1) <= Double.parseDouble(expectedValue1);
                        assertTip = String.format("断言值：%s，断言关系：%s，期望值：%s,断言结果：%s", assertText1, "小于等于",
                                expectedValue1, b ? "小于等于" : "!小于等于");
                        assertResult.setSuccess(b);
                        assertResult.setAssertTip(assertTip);
                    } else {
                        assertResult.setSuccess(false);
                        assertResult.setAssertTip("请检查 期望值、断言值 的数据格式（必须为数字）");
                    }
                }
                case REGULAR -> {
                    boolean matches = assertText1.matches(expectedValue1);
                    assertTip = String.format("断言值：%s，断言关系：%s，正则表达式：%s,断言结果：%s", assertText1, "正则匹配",
                            expectedValue1,
                            matches ? "匹配成功" : "匹配失败");
                    assertResult.setSuccess(matches);
                    assertResult.setAssertTip(assertTip);
                }
                default -> throw new RuntimeException("不支持的断言关系 ：" + assertRelationship);
            }
        }


        return assertResult;
    }

    @Override
    public String getType() {
        return "custom";
    }
}
