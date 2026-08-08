package com.mokatest.platform.demos.step.abstractStep;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorType;
import com.mokatest.platform.demos.config.ApplicationContextHolder;
import com.mokatest.platform.demos.config.DebugWebSocketHandler;
import com.mokatest.platform.demos.debug.PlaywrightDebugSession;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.element.AiLocatorHealService;
import com.mokatest.platform.demos.element.ElementProcessor;
import com.mokatest.platform.demos.result.StepResult;
import com.google.gson.JsonObject;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.TimeoutError;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author JingLong
 * @Description 需要元素的步骤
 * @Date 2025/10/16 20:14
 **/
@Slf4j
public abstract class AbstractNecessaryElementTestStep extends AbstractTestStep {
    protected ElementDTO element; // 操作元素

    /**
     * AI 自愈命中的临时定位器：仅用于本次重试，不持久化
     */
    private transient Element healedLocator;

    public AbstractNecessaryElementTestStep(ElementDTO element) {
        this.element = element;
    }


    protected Locator getLocator(TestExecutionContext context) {
        ElementProcessor bean = ApplicationContextHolder.getBean(ElementProcessor.class);
        // 自愈重试期间优先使用 AI 验证过的新定位器
        Element elementLocator = healedLocator != null ? healedLocator : bean.getElementLocator(element);
        Locator locator = getLocator(context.getCurrentFrame(),
                ElementLocatorType.valueOf(elementLocator.getLocatorType().toString().toUpperCase()),
                elementLocator.getLocatorValue());
        return locator;
    }

    /**
     * 元素类步骤主逻辑：定位相关失败时先尝试 AI 定位自愈，命中后原地重试一次。
     * 修不了时优先用 AI 诊断作为失败原因（直接写入步骤结果并返回 FAILURE，
     * 不再抛异常，避免异常语义差异影响调试会话的失败挂起流程）；
     * AI 未配置/不可用时回退原始异常（通用超时错误，行为与未接入 AI 完全一致）。
     */
    @Override
    protected StepResult executeMain(TestExecutionContext context) {
        try {
            return doExecute(context);
        } catch (Exception e) {
            if (!isLocatorFailure(e)) {
                throw e;
            }
            AiLocatorHealService.HealResult healResult = tryAiHeal(context, e);
            if (healResult == null) {
                // AI 未配置/不可用：按原异常返回（通用超时错误）
                throw e;
            }
            if (healResult.element == null) {
                // AI 修不了：诊断写入步骤结果，按 FAILURE 正常收尾（等价于断言失败路径）
                if (healResult.diagnosis != null && !healResult.diagnosis.isEmpty()) {
                    context.getCurrentCommonStepResult().setErrorMessage("AI 诊断：" + healResult.diagnosis);
                    context.getCurrentCommonStepResult().setStatus(StepExecutionType.FAILURE);
                    context.tryStopContextContineExecution();
                    return context.getCurrentStepResult();
                }
                throw e;
            }
            this.healedLocator = healResult.element;
            try {
                StepResult result = doExecute(context);
                notifyHealSuccess(context, healResult.element);
                return result;
            } finally {
                this.healedLocator = null;
            }
        }
    }

    /**
     * 仅对「定位器类失败」触发自愈：等待元素超时 / strict mode 匹配到多个元素。
     * 断言失败、脚本错误等不属于定位问题，不触发自愈。
     */
    private boolean isLocatorFailure(Exception e) {
        if (e instanceof TimeoutError) {
            return true;
        }
        String msg = e.getMessage();
        return msg != null && (msg.contains("waiting for locator") || msg.contains("strict mode violation"));
    }

    private AiLocatorHealService.HealResult tryAiHeal(TestExecutionContext context, Exception error) {
        try {
            AiLocatorHealService healService = ApplicationContextHolder.getBean(AiLocatorHealService.class);
            return healService.tryHeal(context, this.element, getStepName(), error);
        } catch (Exception ex) {
            log.warn("[AiHeal] 步骤[{}] 自愈服务调用异常，按原失败处理: {}", getStepName(), ex.getMessage());
            return null;
        }
    }

    /**
     * 自愈重试成功后推送修复建议（仅调试会话），由前端人工确认后写回元素库
     */
    private void notifyHealSuccess(TestExecutionContext context, Element healed) {
        if (!(context instanceof PlaywrightDebugSession debugSession)) {
            return;
        }
        try {
            ElementProcessor processor = ApplicationContextHolder.getBean(ElementProcessor.class);
            Element old = processor.getElementLocator(element);

            JsonObject msg = new JsonObject();
            msg.addProperty("type", "HEAL_SUGGESTED");
            msg.addProperty("stepId", getId());
            msg.addProperty("stepName", getStepName());
            msg.addProperty("locatorSource", element.getLocatorSource());
            if (element.getLocator() != null && element.getLocator().getId() != null) {
                msg.addProperty("elementId", element.getLocator().getId());
            }
            msg.addProperty("elementName", old.getElementName());
            msg.addProperty("oldType", String.valueOf(old.getLocatorType()).toUpperCase());
            msg.addProperty("oldValue", old.getLocatorValue());
            msg.addProperty("newType", String.valueOf(healed.getLocatorType()).toUpperCase());
            msg.addProperty("newValue", healed.getLocatorValue());
            DebugWebSocketHandler.sendToSession(debugSession.getSessionId(), msg.toString());
        } catch (Exception e) {
            log.warn("[AiHeal] 推送修复建议失败: {}", e.getMessage());
        }
    }
}
