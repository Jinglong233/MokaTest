package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.dto.step.ElementDomOperationStepDTO;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementDomOperationType;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.StylePriority;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractNecessaryElementTestStep;
import com.mokatest.platform.demos.util.VariableReplacer;
import com.microsoft.playwright.Locator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 元素DOM操作步骤
 * <p>
 * 通过 locator.evaluate 在浏览器端直接执行 DOM 操作，支持：
 * 设置属性 / 移除属性 / 设置内联样式（支持 !important）/
 * 追加CSS类 / 移除CSS类 / 触发原生事件
 * <p>
 * 值类字段（属性值、样式值、类名等）执行前统一走 VariableReplacer 替换 {{变量名}}
 */
public class ElementDomOperationStep extends AbstractNecessaryElementTestStep {

    private final ElementDomOperationStepDTO operationInfo;

    public ElementDomOperationStep(ElementDomOperationStepDTO operationInfo) {
        super(operationInfo.getElement());
        this.operationInfo = operationInfo;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();

        ElementDomOperationType operationType = operationInfo.getOperationType();
        if (operationType == null) {
            throw new RuntimeException("未配置元素DOM操作类型");
        }

        Locator locator = getLocator(context);
        switch (operationType) {
            case SET_ATTRIBUTE -> setAttribute(locator, context);
            case REMOVE_ATTRIBUTE -> removeAttribute(locator, context);
            case SET_STYLE -> setStyle(locator, context);
            case ADD_CLASS -> modifyClass(locator, context, true);
            case REMOVE_CLASS -> modifyClass(locator, context, false);
            case DISPATCH_EVENT -> dispatchEvent(locator, context);
            default -> throw new RuntimeException("暂不支持的元素DOM操作类型: " + operationType);
        }

        context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        return result;
    }

    /**
     * 设置属性：element.setAttribute(name, value)
     * <p>
     * 属性值允许空字符串；属性名和属性值均支持 {{变量名}} 替换
     */
    private void setAttribute(Locator locator, TestExecutionContext context) {
        String name = replace(requireText(operationInfo.getAttributeName(), "属性名"), context);
        // 属性值为 null 时按空字符串处理（setAttribute 允许空字符串）
        String value = operationInfo.getAttributeValue() == null ? "" : replace(operationInfo.getAttributeValue(), context);

        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("value", value);
        locator.evaluate("(el, args) => el.setAttribute(args.name, args.value)", args);
    }

    /**
     * 移除属性：element.removeAttribute(name)
     * <p>
     * 属性不存在时浏览器不报错，视为执行成功
     */
    private void removeAttribute(Locator locator, TestExecutionContext context) {
        String name = replace(requireText(operationInfo.getAttributeName(), "属性名"), context);

        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        locator.evaluate("(el, args) => el.removeAttribute(args.name)", args);
    }

    /**
     * 设置内联样式：
     * 普通优先级：element.style[name] = value
     * !important：element.style.setProperty(name, value, 'important')
     */
    private void setStyle(Locator locator, TestExecutionContext context) {
        String name = replace(requireText(operationInfo.getStyleName(), "CSS属性名"), context);
        String value = operationInfo.getStyleValue() == null ? "" : replace(operationInfo.getStyleValue(), context);
        boolean important = StylePriority.IMPORTANT == operationInfo.getStylePriority();

        Map<String, Object> args = new HashMap<>();
        args.put("name", name);
        args.put("value", value);
        if (important) {
            locator.evaluate("(el, args) => el.style.setProperty(args.name, args.value, 'important')", args);
        } else {
            locator.evaluate("(el, args) => { el.style[args.name] = args.value; }", args);
        }
    }

    /**
     * 追加 / 移除 CSS 类：element.classList.add/remove(...names)
     * <p>
     * 追加不会覆盖原有 class；移除仅删除指定类名
     */
    private void modifyClass(Locator locator, TestExecutionContext context, boolean add) {
        List<String> classNames = operationInfo.getClassNames();
        if (classNames == null || classNames.isEmpty()) {
            throw new RuntimeException("未配置CSS类名");
        }
        List<String> names = new ArrayList<>();
        for (String className : classNames) {
            if (className == null || className.isBlank()) {
                continue;
            }
            names.add(replace(className, context));
        }
        if (names.isEmpty()) {
            throw new RuntimeException("未配置CSS类名");
        }

        Map<String, Object> args = new HashMap<>();
        args.put("names", names);
        if (add) {
            locator.evaluate("(el, args) => el.classList.add(...args.names)", args);
        } else {
            locator.evaluate("(el, args) => el.classList.remove(...args.names)", args);
        }
    }

    /**
     * 触发原生事件：element.dispatchEvent(new Event(type, { bubbles, cancelable }))
     * <p>
     * bubbles / cancelable 未配置时默认 true
     */
    private void dispatchEvent(Locator locator, TestExecutionContext context) {
        String eventType = replace(requireText(operationInfo.getEventType(), "事件类型"), context);

        Map<String, Object> args = new HashMap<>();
        args.put("type", eventType);
        args.put("bubbles", operationInfo.getEventBubbles() == null || operationInfo.getEventBubbles());
        args.put("cancelable", operationInfo.getEventCancelable() == null || operationInfo.getEventCancelable());
        locator.evaluate("(el, args) => el.dispatchEvent(new Event(args.type, { bubbles: args.bubbles, cancelable: args.cancelable }))", args);
    }

    /**
     * 必填文本校验
     */
    private String requireText(String text, String fieldLabel) {
        if (text == null || text.isEmpty()) {
            throw new RuntimeException("未配置" + fieldLabel);
        }
        return text;
    }

    /**
     * 变量替换（{{变量名}} / ${变量名} / 公共函数）
     */
    private String replace(String text, TestExecutionContext context) {
        return VariableReplacer.replace(text, context.getVariables());
    }
}
