package com.mokatest.platform.demos.step.abstractStep;

import com.mokatest.platform.demos.domain.ui.uiEnum.ScreenshotConfig;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorType;
import com.mokatest.platform.demos.condation.ConditionBuilder;
import com.mokatest.platform.demos.condation.TestCondition;
import com.mokatest.platform.demos.config.ApplicationContextHolder;
import com.mokatest.platform.demos.config.DebugWebSocketHandler;
import com.mokatest.platform.demos.debug.DebugSessionReloadException;
import com.mokatest.platform.demos.debug.PlaywrightDebugSession;
import com.mokatest.platform.demos.debug.PlaywrightPlanSession;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.Setting;
import com.mokatest.platform.demos.domain.ui.dto.step.AssertStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.ExtractStepDTO;
import com.mokatest.platform.demos.element.ElementLocatorProcessor;
import com.mokatest.platform.demos.extract.StepExtractExecuter;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.result.BaseStepResult;
import com.mokatest.platform.demos.result.DebugStepResult;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.util.ErrorAnalysis;
import com.mokatest.platform.demos.util.PlaywrightErrorParser;
import com.google.gson.Gson;
import com.microsoft.playwright.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

// 测试步骤基类

@Slf4j
@Data
public abstract class AbstractTestStep implements Serializable {


    private static final long serialVersionUID = -7812986757632359530L;
    private static final Gson GSON = new Gson();
    private Integer id;

    private Integer parentId;

    private Integer isDisable;

    private String stepName;

    // 设置
    protected Setting setting;

    // 断言列表
    protected List<AssertStepDTO> assertList;
    // 抽取列表
    protected List<ExtractStepDTO> extractList;

    // 执行前等待时间
    private Integer preWaitTime;

    // 执行后等待时间
    private Integer postWaitTime;

    private StepType stepType;

    private Integer orderIndex;

    public StepResult execute(TestExecutionContext context) {
        StepResult currentIsExectionStep = null;
        BaseStepResult result = null;
        long start = System.currentTimeMillis();
        try {
            // 在调试模式下检查暂停命令
            if (context instanceof PlaywrightDebugSession debugSession) {
                debugSession.checkDebugCommands(this);
                // 记录最后开始执行的步骤，作为暂停续跑热加载的回退锚点
                debugSession.setLastExecutedStepId(this.id);
            }

            // 执行前置
            if (preExecute(context)) {
                result = context.getCurrentCommonStepResult();
                StepResult currentStepResult = context.getCurrentStepResult();
                Frame currentFrame = context.getCurrentFrame();
                result.setPageUrl(currentFrame.page().url());
                result.setFrameUrl(currentFrame.url());
                // 执行主逻辑（元素类步骤在 executeMain 中带 AI 定位自愈重试）
                executeMain(context);

                if (context instanceof PlaywrightDebugSession && context.getCompletedResultQueue().size() > 0) {
                    context.setCurrentStepResult(context.getCompletedResultQueue().peek());
                    currentStepResult = context.getCompletedResultQueue().peek();
                }
                // 只有主逻辑成功才执行后置(IF判断不进行后置步骤的执行)
                if (context.getCurrentCommonStepResult().getStatus() == StepExecutionType.SUCCESS) {
                    postExecute(context);
                }
            }
        } catch (Exception e) {
            // 调试热加载信号：直接向上抛（不标记失败、不中断执行），由 executeTest 顶层循环处理续跑
            if (DebugSessionReloadException.isCausedBy(e)) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e);
            }
            context.getCurrentCommonStepResult().setErrorMessage(String.format("执行异常：%s", e.getMessage()));
            e.printStackTrace();
            if (context instanceof PlaywrightDebugSession && context.getCompletedResultQueue().size() > 0) {
                context.setCurrentStepResult(context.getCompletedResultQueue().peek());
            }


            context.getCurrentCommonStepResult().setStatus(StepExecutionType.FAILURE);
            context.tryStopContextContineExecution();

            if (e instanceof TimeoutError) {
                // 处理等待超时的问题
                ErrorAnalysis analysis = PlaywrightErrorParser.parseFullError(e.getMessage());
                result.setErrorMessage(analysis.toString());
            }
        } finally {
            // 热加载回退期间：跳过截图、结果回传与队列弹出，避免产生虚假执行记录，
            // 由 executeTest 捕获热加载信号后统一清理执行状态
            boolean hotReloading = context instanceof PlaywrightDebugSession debugSession
                    && debugSession.isHotReloadInProgress();
            if (!hotReloading) {
            // 「执行到此步骤」：步骤执行完成时，若是目标步骤则标记在下一个步骤前自动暂停
            if (context instanceof PlaywrightDebugSession debugSession) {
                debugSession.markReachedIfTarget(this.id);
            }
            stepScreenshot(context);
            currentIsExectionStep = context.getCurrentStepResult();
            // 获取当前步骤的状态
            BaseStepResult currentCommonStepResult = context.getCurrentCommonStepResult();
            StepExecutionType status = currentCommonStepResult.getStatus();
            // 更新报告状态
            if (context instanceof PlaywrightPlanSession) {
                if (status == StepExecutionType.SUCCESS) {
                    updateSuccessStepCount(context);
                } else if (status == StepExecutionType.FAILURE) {
                    updateFailedStepCount(context);

                }
            }
            long finish = System.currentTimeMillis();
            long timeElapsed = finish - start;
            result.setTimeConsuming(timeElapsed);
            // 回传测试结果（只在调试阶段返回）
            if (context instanceof PlaywrightDebugSession debugSession && context.getCompletedResultQueue().size() > 0) {
                // 判断如果是父步骤，则不进行回传，因为在AbstractChildrenStep.executeChildrenStep方法中已经进行了回传
                DebugWebSocketHandler.sendToSession(debugSession.getSessionId(),
                        GSON.toJson(DebugStepResult.build(currentIsExectionStep)));
            }


            // 重置当前步骤（这是最后一步，这个步骤之后不要再有任何操作）
            if (context.getCompletedResultQueue().size() > 0) {
                context.getCompletedResultQueue().pop();
                if (context.getCompletedResultQueue().size() > 0) {
                    context.setCurrentStepResult(context.getCompletedResultQueue().peek());
                }
            }
            }

        }
        return currentIsExectionStep;
    }

    protected abstract StepResult doExecute(TestExecutionContext context);

    /**
     * 主逻辑执行入口（execute 模板调用点）。
     * 默认直接执行 doExecute；元素类步骤（AbstractNecessaryElementTestStep）覆写此方法，
     * 在定位失败时先尝试 AI 定位自愈再重试一次。
     */
    protected StepResult executeMain(TestExecutionContext context) {
        return doExecute(context);
    }


    public boolean preExecute(TestExecutionContext context) {
        context.increment();
        StepResult stepResult = null;
        // 判断是否是计划执行
        stepResult = getStepResultByStepId(context, this.id);

        BaseStepResult baseStepResult = initStepResult(context, stepResult);
        // 判断是否处于循环中
        if (stepResult.getIsLoop() > 0) {
            // 新建一个结果链表元素
            stepResult.getIterations().put(stepResult.getIterations().size() + 1, baseStepResult);
        } else {
            stepResult.setResult(baseStepResult);
        }
        context.getCompletedResultQueue().push(stepResult);
        try {

            // 赋值给上下文
            context.setCurrentStepResult(stepResult);
            // 设置执行前等待时间
            setPreWaitTime(context);
            // 设置超时时间
            setTimeout(context);

            return true;
        } catch (Exception e) {
            // 调试热加载信号：直接向上抛，由 executeTest 顶层循环处理续跑
            if (DebugSessionReloadException.isCausedBy(e)) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException(e);
            }
            context.getCurrentCommonStepResult().setStatus(StepExecutionType.FAILURE);
            context.tryStopContextContineExecution();
            return false;
        }
    }

    public boolean postExecute(TestExecutionContext context) {
        StepResult currentStepResult = context.getCurrentStepResult();
        BaseStepResult result = context.getCurrentCommonStepResult();
        // 设置后置等待时间
        setPostWaitTime(context);

        ConditionBuilder conditionBuilder = ApplicationContextHolder.getBean(ConditionBuilder.class);
        List<TestCondition> testConditions = conditionBuilder.loadCondition(assertList);
        Integer count = 1;
        // 执行断言
        for (TestCondition testCondition : testConditions) {
            AssertResult evaluate = null;
            try {
                evaluate = testCondition.evaluate(context);
                result.getAssertResults().put(count++, evaluate);
                if (!evaluate.getSuccess()) {
                    context.getCurrentCommonStepResult().setStatus(StepExecutionType.FAILURE);
                    context.tryStopContextContineExecution();
                    result.setErrorMessage(String.format("断言失败:%s", evaluate.getAssertTip()));
                }
            } catch (Exception e) {
                // 在这里捕获异常，是因为断言分为主步骤的断言，和后置的断言。这里捕获后置的断言异常。保证断言失败的时候
                context.getCurrentCommonStepResult().setStatus(StepExecutionType.FAILURE);
                context.tryStopContextContineExecution();
                result.getAssertResults().put(count++, new AssertResult(false, e.getMessage()));
                result.setErrorMessage(String.format("执行异常：%s", e.getMessage()));

            } finally {
                // 更新断言失败成功数量
                if (evaluate != null && evaluate.getSuccess()) {
                    updateSuccessAssertCount(context);
                } else {
                    updateFailedAssertCount(context);
                }
            }
        }

        // 执行抽取
        StepExtractExecuter stepExtractExecuter = ApplicationContextHolder.getBean(StepExtractExecuter.class);
        if (extractList == null || extractList.isEmpty()) {
            return true;
        }
        for (ExtractStepDTO stepExtractor : extractList) {
            try {
                Map<String, Object> execute = (Map<String, Object>) stepExtractExecuter.execute(context, stepExtractor);
                if (execute != null && !execute.isEmpty()) {
                    for (Map.Entry<String, Object> entry : execute.entrySet()) {
                        context.getCurrentCommonStepResult().getExtractResults().put(entry.getKey(), entry.getValue());
                    }
                }
            } catch (Exception e) {
                // 抽取异常捕获
                context.getCurrentCommonStepResult().setStatus(StepExecutionType.FAILURE);
                context.tryStopContextContineExecution();
                context.getCurrentCommonStepResult().getExtractResults().put(false, e.getMessage());
                context.getCurrentCommonStepResult().setErrorMessage(e.getMessage());
            }
        }
        return true;
    }


    protected Locator getLocator(Frame frame, ElementLocatorType locatorType, String selector) {
        return ElementLocatorProcessor.process(frame, locatorType, selector);
    }


    private boolean settingIsEnable() {
        return setting != null && setting.getIsSetting() != 0;
    }

    private void screenshot(TestExecutionContext context) {
        if (context.getCurrentPage() != null && context.isCaptureScreenshots()) {
            StepResult stepResult = context.getCurrentStepResult();
            stepResult.setSceneId(context.getScenarioId());

            // 直接获取截图的字节数组
            byte[] screenshotBytes = context.getCurrentPage().screenshot();

            File tempFile = null;
            try {
                com.mokatest.platform.demos.storage.FileStorageService storageService =
                        com.mokatest.platform.demos.config.ApplicationContextHolder.getBean(
                                com.mokatest.platform.demos.storage.FileStorageService.class);

                String fileName = stepResult.getStep().getId().toString() + "_" + context.getStepIndex() + ".png";
                String path = String.format("screenshots/%s/%s",
                        context.getScenarioId() != null ? String.valueOf(context.getScenarioId()) : "unknown", fileName);

                String fileId = storageService.upload(screenshotBytes, path);
                log.info("截图上传成功，fileId: {}", fileId);
                context.getCurrentCommonStepResult().setScreenshotPath(fileId);
            } catch (Exception e) {
                log.error("截图上传失败", e);
            }
        }
    }


    protected StepResult getStepResultByStepId(TestExecutionContext context, Integer stepId) {
        List<StepResult> stepResults = context.getStepResults();
        if (stepResults == null || stepResults.isEmpty()) return null;
        for (StepResult stepResult : stepResults) {
            if (stepResult.getStep().getId().equals(stepId)) {
                return stepResult;
            }
            // 递归查找
            StepResult childrenResult = getChildrenStepResult(stepResult.getChildren(), stepId);
            if (childrenResult != null) {
                return childrenResult;
            }
        }
        return null;
    }

    private StepResult getChildrenStepResult(List<StepResult> childrenStepResults, Integer stepId) {
        if (childrenStepResults == null || childrenStepResults.isEmpty()) return null;
        for (StepResult childrenStepResult : childrenStepResults) {
            if (childrenStepResult.getStep().getId().equals(stepId)) {
                return childrenStepResult;
            }
            StepResult result = getChildrenStepResult(childrenStepResult.getChildren(), stepId);
            if (result != null) {
                return result;
            }
        }
        return null;
    }


    // 设置超时时间
    private void setTimeout(TestExecutionContext context) {
        double timeout = 30;
        // 先读取场景级超时时间
        if (context.getSceneSetting().getSetting() != null) {
            timeout = context.getSceneSetting().getSetting().getTimeout() == 0 ? 30 :
                    context.getSceneSetting().getSetting().getTimeout();
        }
        // 步骤级设置启用且指定了非 0 超时，才覆盖；0 或 null 都回退到场景级/默认值
        if (settingIsEnable() && this.setting != null) {
            double stepTimeout = this.setting.getTimeout();
            if (stepTimeout > 0) {
                timeout = stepTimeout;
            }
        }

        context.getCurrentPage().setDefaultTimeout((long) (timeout * 1000));

    }


    // 设置前置等待时间
    private void setPreWaitTime(TestExecutionContext context) {
        double preWaitTime = 0;
        if (context.getSceneSetting().getSetting() != null) {
            preWaitTime = context.getSceneSetting().getSetting().getPreExecuteWaitingTime();
        }
        if (settingIsEnable()) {
            preWaitTime = this.setting.getPreExecuteWaitingTime();
        }
        Frame currentFrame = context.getCurrentFrame();
        currentFrame.waitForTimeout(preWaitTime * 1000);
    }


    // 设置后置等待时间
    private void setPostWaitTime(TestExecutionContext context) {
        double postWaitTime = 0;
        if (context.getSceneSetting().getSetting() != null) {
            postWaitTime = context.getSceneSetting().getSetting().getWaitingTimeAfterExecution();
        }
        if (settingIsEnable()) {
            postWaitTime = this.setting.getWaitingTimeAfterExecution();
        }
        Frame currentFrame = context.getCurrentFrame();
        // 检查浏览器、上下文和页面是否仍然可用
        if (currentFrame != null && !currentFrame.isDetached()) {
            Page page = currentFrame.page();
            if (page != null && !page.isClosed()) {
                BrowserContext contextObj = page.context();
                if (contextObj != null && contextObj.browser() != null && contextObj.browser().isConnected()) {
                    currentFrame.waitForTimeout(postWaitTime * 1000);
                }
            }
        }
    }


    /**
     * 步骤截图
     * 优先使用步骤级设置中的截图策略，未启用或未设置时回退到场景级设置。
     *
     * @param context
     */
    protected void stepScreenshot(TestExecutionContext context) {
        ScreenshotConfig screenshotConfig = null;

        // 1. 优先读取步骤级设置
        if (settingIsEnable() && this.setting != null && this.setting.getScreenshotConfiguration() != null) {
            screenshotConfig = this.setting.getScreenshotConfiguration();
        }

        // 2. 步骤级未启用或未设置，回退到场景级设置
        if (screenshotConfig == null
                && context.getSceneSetting() != null
                && context.getSceneSetting().getSetting() != null
                && context.getSceneSetting().getSetting().getScreenshotConfiguration() != null) {
            screenshotConfig = context.getSceneSetting().getSetting().getScreenshotConfiguration();
        }

        if (screenshotConfig == null) {
            return;
        }

        switch (screenshotConfig) {
            case SCREENSHOT:
                screenshot(context);
                break;
            case SCREENSHOT_EXCEPTION:
                // 仅在步骤执行失败时截图
                if (context.getCurrentCommonStepResult().getStatus() == StepExecutionType.FAILURE) {
                    screenshot(context);
                }
                break;
            case NOT_SCREENSHOT:
            default:
                // 不截图
                break;
        }
    }


    /**
     * 判断是否需要执行后置步骤
     *
     * @param stepType
     * @return
     */
    protected boolean isIgnorePostStep(StepType stepType) {
        return stepType == StepType.IF;
    }

    /**
     * 更新成功断言数量（不再实时写入数据库，最后统一统计）
     *
     * @param context
     */
    protected void updateSuccessAssertCount(TestExecutionContext context) {
        // 计划执行模式下，断言计数不再实时写库，避免并行 lost update
        // 最终结果在 TaskManager 的 finally 中统一从 stepResults 统计
    }


    /**
     * 更新失败断言数量（不再实时写入数据库，最后统一统计）
     *
     * @param context
     */
    protected void updateFailedAssertCount(TestExecutionContext context) {
        // 计划执行模式下，断言计数不再实时写库，避免并行 lost update
        // 最终结果在 TaskManager 的 finally 中统一从 stepResults 统计
    }


    // 更新失败步骤数量（不再实时写入数据库，最后统一统计）
    private void updateFailedStepCount(TestExecutionContext context) {
        // 计划执行模式下，步骤计数不再实时写库，避免并行 lost update
        // 最终结果在 TaskManager 的 finally 中统一从 stepResults 统计
    }


    // 更新成功步骤数量（不再实时写入数据库，最后统一统计）
    private void updateSuccessStepCount(TestExecutionContext context) {
        // 计划执行模式下，步骤计数不再实时写库，避免并行 lost update
        // 最终结果在 TaskManager 的 finally 中统一从 stepResults 统计
    }

    /**
     * 初始化步骤结果
     *
     * @return
     */
    public BaseStepResult initStepResult(TestExecutionContext context, StepResult stepResult) {
        // 只有在Debug模式才生效
        BaseStepResult baseStepResult = new BaseStepResult();
        if (context instanceof PlaywrightDebugSession) {
            if (StepType.WHILE.equals(stepResult.getStep().getStepType()) || StepType.FOR.equals(stepResult.getStep().getStepType())) {
                baseStepResult.setStatus(StepExecutionType.SUCCESS);
            }
        }
        return baseStepResult;
    }


    @Override
    public String toString() {
        return "AbstractTestStep{" +
                "id=" + id +
                ", parentId=" + parentId +
                ", isDisable=" + isDisable +
                ", stepName='" + stepName + '\'' +
                ", setting=" + setting +
                ", assertList=" + assertList +
                ", extractList=" + extractList +
                ", preWaitTime=" + preWaitTime +
                ", postWaitTime=" + postWaitTime +
                ", stepType=" + stepType +
                ", orderIndex=" + orderIndex +
                '}';
    }
}
