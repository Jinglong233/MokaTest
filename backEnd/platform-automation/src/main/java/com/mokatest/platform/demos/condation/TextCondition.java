package com.mokatest.platform.demos.condation;

import com.mokatest.platform.demos.domain.ui.uiEnum.condation.AssertType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.util.VariableReplacer;
import com.microsoft.playwright.Frame;
import lombok.Data;

import java.io.Serializable;


/**
 * 文本条件
 */
@Data
public class TextCondition implements TestCondition, Serializable {


    private static final long serialVersionUID = -5898121481291730569L;
    /**
     * 断言类型
     */
    private AssertType assertType;

    /**
     * 断言文本
     */
    private String assertText;

    public TextCondition(AssertType assertType, String assertText) {
        this.assertType = assertType;
        this.assertText = assertText;
    }

    @Override
    public AssertResult evaluate(TestExecutionContext context) {
        try {
            String assertTip = "";
            // 统一替换：先变量后函数，二者可混用
            String finalValue = VariableReplacer.replace(assertText, context.getVariables());

            Frame currentFrame = context.getCurrentFrame();
            AssertResult assertResult = new AssertResult();
            assertResult.setAssertType(assertType.getName());
            switch (assertType) {
                case TEXT_EXIST -> {
                    boolean visible = currentFrame.getByText(finalValue).count() > 0;
                    assertTip = String.format("断言类型：%s，断言文本：%s，断言结果：%s", "文本存在", finalValue, visible ?
                            "文本存在" : "文本不存在");
                    assertResult.setAssertTip(assertTip);
                    assertResult.setSuccess(visible);
                }
                case TEXT_NOT_EXIST -> {
                    boolean visible = currentFrame.getByText(finalValue).count() > 0;
                    assertTip = String.format("断言类型：%s，断言文本：%s，断言结果：%s", "文本不存在", finalValue, visible ?
                            "文本存在" : "文本不存在");
                    assertResult.setAssertTip(assertTip);
                    assertResult.setSuccess(!visible);
                }
                default -> throw new RuntimeException("不支持的文本断言类型：" + finalValue);
            }
            return assertResult;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public String getType() {
        return "text";
    }
}