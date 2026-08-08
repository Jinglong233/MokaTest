package com.mokatest.platform.demos.debug;

import com.mokatest.platform.demos.domain.ui.uiEnum.DebuggerState;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepErrorHandleStrategy;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.debug.ExecuteType;
import com.mokatest.platform.demos.domain.ui.uiEnum.task.SceneExecuteType;
import com.mokatest.platform.demos.domain.ui.Plan;
import com.mokatest.platform.demos.domain.ui.Report;
import com.mokatest.platform.demos.domain.ui.SceneSetting;
import com.mokatest.platform.demos.mapper.ReportMapper;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.Node.StepTreeNode;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.util.SpringContextHolder;
import com.google.gson.Gson;
import com.microsoft.playwright.Page;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/9/23 17:15
 **/
@Data
@Slf4j
public class PlaywrightPlanSession extends TestExecutionContext {


    private CountDownLatch latch;

    protected final ExecuteType executeType = ExecuteType.PLAN;

    // 场景配置（计划配置）
    private SceneSetting sceneSetting;

    // 场景执行类型
    private SceneExecuteType sceneExecuteType;

    // 计划配置
    private Plan plan;

    // 关联的报告id
    private Integer reportId;

    // 浏览器上下文认证状态
    private String authContext;

    // 强制终止标记：主线程超时强关时置为 true，worker 在步骤执行间隙检查主动退出
    private volatile boolean forceTerminated = false;

    // 认证相关的步骤节点
    private List<StepTreeNode> authStep = new ArrayList<>();

    /**
     * 批量写库计数器，串行模式下每 {@link #getBatchWriteSize()} 步写一次数据库，
     * 减少频繁全量 JSON 读写带来的性能开销。
     */
    private int stepWriteCounter = 0;
    private static final int DEFAULT_BATCH_WRITE_SIZE = 5;

    public PlaywrightPlanSession() {
        super();
    }

    /**
     * 获取当前场景的步骤结果（用于计划执行完后统一收集）
     * 返回拷贝避免外部并发修改导致的 ConcurrentModificationException
     */
    public List<StepResult> getSceneStepResults() {
        synchronized (this.stepResults) {
            return new ArrayList<>(this.stepResults);
        }
    }

    /**
     * 根据当前场景的步骤数动态计算批量写库大小。
     *   小场景（&lt;=3 步）：每步写库，保证步骤级实时性
     *   中等场景（4~10 步）：每 2 步写，折中性能与实时性
     *   大场景（&gt;10 步）：每 5 步写，减少数据库压力
     */
    private int getBatchWriteSize() {
        int stepCount = this.testSteps != null ? this.testSteps.size() : 0;
        if (stepCount <= 3) {
            return 1;
        }
        if (stepCount <= 10) {
            return 2;
        }
        return DEFAULT_BATCH_WRITE_SIZE;
    }

    @Override
    public void executeTest() {
        try {
            // 启动浏览器
            context = createContext();
            log.info("场景：{},线程：{},浏览器:{},上下文:{},playwright：{}", this.getSceneName(), Thread.currentThread().getName(), browser, context, playwright);

            // 创建初始页面
            Page firstPage = context.newPage();
            allPages.add(firstPage);
            setupPageListeners(firstPage);

            // 将所有的步骤结果添加到stepResults中
            this.stepResults = initialStepResults(this.testSteps);
            // 每个场景开始时重置计数器，避免跨场景累加导致写库时机不可预期
            this.stepWriteCounter = 0;

            // 执行测试步骤
            for (int i = 0; i < testSteps.size(); i++) {
                // 主线程超时强关时主动退出，避免访问已被关闭的 browser/context
                if (forceTerminated) {
                    log.warn("场景[{}]被强制终止，停止后续步骤执行", this.getSceneName());
                    break;
                }
                // 2. 执行步骤
                AbstractTestStep executableStep = testSteps.get(i).getExecutableStep();
                // 2.1 执行具体
                // 判断当前步骤是否需要跳过
                Integer isDisable = executableStep.getIsDisable();
                if (isDisable == 1) {
                    continue;
                }
                // 判断是否还有页面存活
                if (allPages.isEmpty()) {
                    break;
                }
                StepResult result = executableStep.execute(this);

                // 处理步骤结果
                if (result.getIterations().isEmpty() ? result.getResult().getStatus() == StepExecutionType.FAILURE :
                        result.getIterations().get(result.getIterations().size()).getStatus() == StepExecutionType.FAILURE) {
                    // 异常处理策略
                    StepErrorHandleStrategy stepErrorHandleStrategy = getStepErrorHandleStrategy();
                    // 暂时只考虑 终止 和 继续 两种情况
                    if (stepErrorHandleStrategy == StepErrorHandleStrategy.STOP) {
                        state = DebuggerState.FAILED;
                        break;
                    }
                }

                // 串行模式：批量写库，根据场景步骤数动态调整批量大小
                if (sceneExecuteType == SceneExecuteType.ORDER) {
                    if (++stepWriteCounter % getBatchWriteSize() == 0) {
                        updateReportSceneResult();
                    }
                }
            }
            // 场景执行完后，确保剩余未批量写入的步骤入库
            if (sceneExecuteType == SceneExecuteType.ORDER && stepWriteCounter > 0 && stepWriteCounter % getBatchWriteSize() != 0) {
                updateReportSceneResult();
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("当前执行异常的场景：", this.getSceneName());
            // 异常时也要把已执行的步骤写入数据库，避免结果丢失
            if (sceneExecuteType == SceneExecuteType.ORDER && stepWriteCounter > 0) {
                updateReportSceneResult();
            }
        } catch (Error error) {
            log.error("当前执行发生error场景：", this.getSceneName());
        } finally {
            // 判断是 运行模式
            if (sceneExecuteType == SceneExecuteType.ORDER) {
                // 串行模式：批量写库已在执行过程中完成，finally 中只需关闭资源
                closeContext();
            } else if (sceneExecuteType == SceneExecuteType.PARALLEL) {
                // 并行：多线程竞争，最后统一写入（避免 lost update）
                // 关闭所有页面
                for (Page page : allPages) {
                    if (!page.isClosed()) {
                        page.close();
                    }
                }
                closeAllResources();
            }
        }
    }


    /**
     * 执行认证相关步骤
     */
    public String executeAuthTest() {
        if (authStep == null) {
            return null;
        }
        try {
            // 启动浏览器
            context = createContext();
            // 创建初始页面
            Page firstPage = context.newPage();
            allPages.add(firstPage);
            setupPageListeners(firstPage);

            // 将所有的步骤结果添加到stepResults中
            this.stepResults = initialStepResults(this.authStep);

            // 执行测试步骤
            for (int i = 0; i < authStep.size(); i++) {
                // 2. 执行步骤
                AbstractTestStep executableStep = authStep.get(i).getExecutableStep();
                // 2.1 执行具体
                // 判断当前步骤是否需要跳过
                Integer isDisable = executableStep.getIsDisable();
                if (isDisable == 1) {
                    continue;
                }
                StepResult result = executableStep.execute(this);

                // 处理步骤结果
                if (result.getIterations().isEmpty() ? result.getResult().getStatus() == StepExecutionType.FAILURE :
                        result.getIterations().get(result.getIterations().size()).getStatus() == StepExecutionType.FAILURE) {
                    // 异常处理策略
                    StepErrorHandleStrategy stepErrorHandleStrategy = getStepErrorHandleStrategy();
                    // 暂时只考虑 终止 和 继续 两种情况
                    if (stepErrorHandleStrategy == StepErrorHandleStrategy.STOP) {
                        state = DebuggerState.FAILED;
                        break;
                    }
                }
            }
            // 存储上下文信息
            this.authContext = context.storageState();
            return this.authContext;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeAllResources();
        }
        return null;
    }


    /**
     * 更新报告场景结果
     */
    public void updateReportSceneResult() {
        try {
            ReportMapper bean = SpringContextHolder.getBean(ReportMapper.class);
            Report report = bean.selectById(this.reportId);
            Gson gson = new Gson();

            // 先获取已有的场景结果
            Map<String, List<StepResult>> scenesResults = new HashMap<>();
            Object existingScenesJson = report.getScenes();
            if (existingScenesJson != null && !existingScenesJson.toString().isEmpty()) {
                // 如果已有场景结果，则解析出来
                scenesResults = gson.fromJson(existingScenesJson.toString(), Map.class);
            }
            // 将当前场景的结果添加到整体结果中，避免覆盖其他场景的结果
            scenesResults.put(this.getScenarioId() + "_" + this.getSceneName(), this.stepResults);

            // 保存
            report.setScenes(gson.toJson(scenesResults));
            // 结果入库
            bean.updateById(report);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    // 异步执行（并行模式使用）
    public boolean startPlan(Integer taskId) {
        executionThread = new Thread(this::executeTest);
        executionThread.start();
        return true;
    }

    // 同步执行（串行模式使用）
    public boolean startPlanAndWait(Integer taskId) {
        executionThread = new Thread(this::executeTest);
        executionThread.start();
        try {
            executionThread.join(); // 等待执行完成
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 执行auth场景
     *
     * @return
     */
    public boolean startAuthExecute() {
        executionThread = new Thread(this::executeAuthTest);
        executionThread.start();
        try {
            executionThread.join(); // 等待执行完成
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 带超时的同步执行
     *
     * @param taskId
     * @param timeoutMillis
     * @return
     */
    public boolean startPlanAndWait(Integer taskId, long timeoutMillis) {
        executionThread = new Thread(this::executeTest);
        executionThread.start();
        try {
            executionThread.join(timeoutMillis);
            return !executionThread.isAlive(); // 返回是否在超时前完成
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }


}
