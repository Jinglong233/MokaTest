package com.mokatest.platform.demos.debug;

import com.mokatest.platform.demos.domain.ui.uiEnum.DebuggerState;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepErrorHandleStrategy;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.debug.ExecuteType;
import com.mokatest.platform.demos.config.ApplicationContextHolder;
import com.mokatest.platform.demos.config.DebugWebSocketHandler;
import com.mokatest.platform.demos.constanst.DebugCommand;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.Node.StepBuilder;
import com.mokatest.platform.demos.step.Node.StepTreeNode;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.google.gson.Gson;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.mokatest.platform.demos.storage.FileStorageService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// 调试会话核心类
@Data
@Slf4j
public class PlaywrightDebugSession extends TestExecutionContext {

    private static final Gson GSON = new Gson();

    protected final ExecuteType executeType = ExecuteType.DEBUG;

    private static final long FAILURE_PAUSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private Integer failedStepId;
    private long failurePauseDeadlineMs;

    /**
     * 最后一个开始执行的步骤 id，作为暂停续跑热加载的回退锚点
     * （暂停发生在步骤边界，该步骤必然已执行完成，前端会锁定不允许删除）
     */
    private Integer lastExecutedStepId;

    /**
     * 热加载回退标记：为 true 时，execute 的 finally 不再做截图/结果回传/队列弹出，
     * 由 executeTest 顶层捕获热加载信号后统一清理
     */
    private boolean hotReloadInProgress = false;

    /**
     * 「执行到此步骤」目标步骤 id：目标步骤执行完成后、下一个步骤执行前自动暂停（触发一次后清空）
     */
    private Integer runUntilStepId;

    /**
     * 目标步骤已执行完成标记：为 true 时，在下一个步骤执行前自动进入暂停
     */
    private boolean pauseAfterTargetReached = false;

    /**
     * 步骤执行完成时调用：若该步骤是「执行到此步骤」的目标，标记下一个步骤前自动暂停。
     *
     * @param stepId 刚执行完成的步骤 id
     */
    public void markReachedIfTarget(Integer stepId) {
        if (runUntilStepId != null && runUntilStepId.equals(stepId)) {
            log.info("[DebugSession {}] 目标步骤 {} 已执行完成，将在下一个步骤前暂停", this.sessionId, stepId);
            runUntilStepId = null;
            pauseAfterTargetReached = true;
        }
    }

    public PlaywrightDebugSession() {
        super();
    }


    // 是否切换到新页面
    public void setAutoSwitchToNewPages(boolean autoSwitch) {
        this.autoSwitchToNewPages = autoSwitch;
    }

    // 设置截图
    public void setCaptureScreenshots(boolean captureScreenshots) {
        this.captureScreenshots = captureScreenshots;
    }

    // 设置无头模式
    public void setHeadless(boolean headless) {
        this.headless = headless;
    }


    // 发送调试命令
    public void sendCommand(String command) {
        commandQueue.offer(command);
    }

    // 获取当前状态
    public DebuggerState getState() {
        return state;
    }

    /**
     * 强制停止调试会话，释放所有资源。
     * 优先让执行线程自然结束以完成资源清理；超时后再强制中断兜底。
     */
    public void forceStop() {
        state = DebuggerState.FINISHED;
        // 等待执行线程自然走到 finally，完成 tracing.stop 和 trace 上传
        if (executionThread != null && executionThread.isAlive()) {
            try {
                executionThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 如果线程仍未结束，强制中断并等待清理
        if (executionThread != null && executionThread.isAlive()) {
            executionThread.interrupt();
            try {
                executionThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 兜底：强制关闭浏览器资源
        if (executionThread != null && executionThread.isAlive()) {
            closeAllResources();
        }
    }

    // 等待调试命令
    private String waitForCommand() {
        try {
            return commandQueue.take();
        } catch (InterruptedException e) {
            return "q";
        }
    }

    // 处理调试命令
    private void handleCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }

        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : null;

        switch (cmd) {
            case DebugCommand.PAUSE: // 停止
                // 失败挂起中收到的边界暂停命令（用户在步骤执行中点了暂停，步骤随后失败）：
                // 此时已无在飞步骤可暂停，忽略该命令，避免误判为「退出挂起」导致流程直接结束
                if (state == DebuggerState.PAUSED_ON_FAILURE) {
                    log.info("[DebugSession {}] 失败挂起中忽略暂停命令", this.sessionId);
                    break;
                }
                state = DebuggerState.PAUSED;
                System.out.println("Stepping to PAUSED");
                break;

            case DebugCommand.CONTINUE: // 继续执行
                state = DebuggerState.RUNNING;
                System.out.println("Continuing execution");
                break;


            case DebugCommand.QUIT: // 退出
                state = DebuggerState.FINISHED;
                System.out.println("Terminating test execution");
                break;
            case DebugCommand.RETRY: // 继续重试
                state = DebuggerState.RUNNING;
                System.out.println("Retrying from failed step");
                break;
            case DebugCommand.UNTIL: // 继续执行到指定步骤前暂停
                if (arg != null) {
                    try {
                        this.runUntilStepId = Integer.valueOf(arg.trim());
                        state = DebuggerState.RUNNING;
                        System.out.println("Run until step " + arg);
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid until step id: " + arg);
                    }
                }
                break;
            default:
                System.out.println("Unknown command: " + command);
                printHelp();
        }
    }


    /**
     * 主测试执行方法
     */
    @Override
    public void executeTest() {
        try {
            context = createContext();

            // 启动 Playwright Tracing（screenshots + snapshots + sources），调试结束后生成 trace.zip
            context.tracing().start(new Tracing.StartOptions()
                    .setTitle("场景调试-" + (sessionId != null ? sessionId : "unknown"))
                    .setScreenshots(true)
                    .setSnapshots(true)
                    .setSources(true));

            // 创建初始页面
            Page firstPage = context.newPage();
            allPages.add(firstPage);
            setupPageListeners(firstPage);

            // 将所有的步骤结果添加到stepResults中
            this.stepResults = initialStepResults(this.testSteps);

            // 执行测试步骤
            int i = 0;
            while (i < testSteps.size()) {
                // 失败挂起状态：等待用户重试或超时
                if (state == DebuggerState.PAUSED_ON_FAILURE) {
                    if (!waitForRetryOrTimeout()) {
                        break;
                    }
                    // 重试成功，定位到失败步骤在当前步骤列表中的顶层祖先位置
                    i = findRetryStartIndex(this.failedStepId);
                    if (i < 0) {
                        state = DebuggerState.FAILED;
                        DebugWebSocketHandler.sendToSession(this.sessionId,
                                GSON.toJson("RETRY_FAILED: 失败步骤已被删除或变更，无法继续重试"));
                        break;
                    }
                    continue;
                }

                // 判断是否还有页面存活
                if (allPages.isEmpty()) {
                    state = DebuggerState.FINISHED;
                }

                if (state == DebuggerState.FINISHED || state == DebuggerState.FAILED) {
                    break;
                }

                // 1. 执行步骤
                AbstractTestStep executableStep = testSteps.get(i).getExecutableStep();

                // 判断是否是禁用步骤
                if (executableStep.getIsDisable() == 1) {
                    i++;
                    continue;
                }

                // 2. 执行具体
                StepResult result;
                try {
                    result = executableStep.execute(this);
                } catch (Exception e) {
                    if (DebugSessionReloadException.isCausedBy(e)) {
                        // 暂停期间步骤发生变更：步骤列表已在 prepareResume 中截断到锚点，从锚点续跑
                        log.info("[DebugSession {}] 捕获热加载信号，从锚点步骤续跑", this.sessionId);
                        i = 0;
                        this.hotReloadInProgress = false;
                        this.getCompletedResultQueue().clear();
                        this.setCurrentStepResult(null);
                        continue;
                    }
                    throw e;
                }

                // 处理步骤结果
                if (result.getIterations().isEmpty() ? result.getResult().getStatus() == StepExecutionType.FAILURE :
                        result.getIterations().get(result.getIterations().size()).getStatus() == StepExecutionType.FAILURE) {
                    StepErrorHandleStrategy stepErrorHandleStrategy = getStepErrorHandleStrategy();
                    if (stepErrorHandleStrategy == StepErrorHandleStrategy.STOP) {
                        failedStepId = executableStep.getId();
                        failurePauseDeadlineMs = System.currentTimeMillis() + FAILURE_PAUSE_TIMEOUT_MS;
                        state = DebuggerState.PAUSED_ON_FAILURE;
                        log.info("[DebugSession {}] 步骤 {} 失败且策略为 STOP，进入 PAUSED_ON_FAILURE",
                                this.sessionId, failedStepId);
                        System.err.println("Step failed: " + result.getResult().getErrorMessage());
                        // 不立即关闭浏览器，继续循环进入挂起等待
                        continue;
                    }
                }

                // 如果处于单步模式，再次暂停
                if (state == DebuggerState.STEPPING) {
                    state = DebuggerState.PAUSED;
                }

                i++;
            }

            // 测试完成
            log.info("[DebugSession {}] 步骤循环结束，最终 state={}", this.sessionId, this.state);
            if (state != DebuggerState.FAILED) {
                state = DebuggerState.FINISHED;
                System.out.println("\nTest execution completed successfully");
            }
        } catch (Exception e) {
            state = DebuggerState.FAILED;
            DebugWebSocketHandler.sendToSession(this.sessionId, GSON.toJson("执行失败，原因：" + e.getMessage()));
            e.printStackTrace();
        } finally {
            // 按正确顺序释放资源：context → browser → playwright
            // 用 try-catch 兜底，确保即使关闭资源异常也会发送「执行结束」
            try {
                // 停止 Tracing 并生成 trace.zip，上传后通过 WebSocket 发给前端
                stopAndUploadTrace();
                closeContext();
                closeBrowser();
                closePlaywright();
            } catch (Exception e) {
                log.error("[DebugSession {}] 关闭浏览器/上下文/Playwright 资源异常", this.sessionId, e);
            }

            // 发送执行结束消息并关闭会话
            log.info("[DebugSession {}] 执行结束，发送执行结束消息，当前 state={}", this.sessionId, this.state);
            DebugWebSocketHandler.sendToSession(this.sessionId, GSON.toJson("执行结束"));
            // 清除会话
            DebugWebSocketHandler.closeSession(this.sessionId);
        }
    }
    
    /** trace 文件自动清理延迟（分钟），调试 trace 是临时数据，到期自动删除 */
    private static final long TRACE_AUTO_DELETE_MINUTES = 30;
    
    /** 延迟删除 trace 文件的调度器（守护线程） */
    private static final ScheduledExecutorService TRACE_CLEANUP_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "trace-cleanup");
                t.setDaemon(true);
                return t;
            });
    
    /**
     * 停止 Tracing 并生成 trace.zip，上传到文件存储后通过 WebSocket 发给前端。
     * 调试 trace 属于临时数据，上传后安排 30 分钟自动删除（随用随删）。
     */
    private void stopAndUploadTrace() {
        if (context == null) {
            return;
        }
        Path tracePath = null;
        try {
            tracePath = Paths.get(System.getProperty("java.io.tmpdir"),
                    "trace_" + (sessionId != null ? sessionId : System.nanoTime()) + ".zip");
            final Path finalTracePath = tracePath;
            context.tracing().stop(new Tracing.StopOptions().setPath(finalTracePath));
    
            if (!Files.exists(finalTracePath)) {
                log.warn("[DebugSession {}] tracing.stop 未生成 trace 文件", sessionId);
                return;
            }
    
            byte[] traceBytes = Files.readAllBytes(finalTracePath);
            Files.deleteIfExists(finalTracePath);
    
            // 上传到文件存储（与截图走同一套逻辑）
            FileStorageService storageService = ApplicationContextHolder.getBean(FileStorageService.class);
            String storagePath = "traces/" + sessionId + "/trace.zip";
            String fileId = storageService.upload(traceBytes, storagePath);
    
            // 通过 WebSocket 发给前端 fileId，前端拼接代理 URL 供 trace.playwright.dev 加载
            String traceMsg = "TRACE:" + fileId;
            DebugWebSocketHandler.sendToSession(sessionId, GSON.toJson(traceMsg));
    
            log.info("[DebugSession {}] trace.zip 已上传，fileId={}，{} 分钟后自动清理",
                    sessionId, fileId, TRACE_AUTO_DELETE_MINUTES);
    
            // 安排延迟删除（随用随删）
            final String fileToDelete = fileId;
            TRACE_CLEANUP_EXECUTOR.schedule(() -> {
                try {
                    boolean deleted = storageService.delete(fileToDelete);
                    log.info("[DebugSession {}] trace 文件自动清理，fileId={}，结果={}",
                            sessionId, fileToDelete, deleted);
                } catch (Exception e) {
                    log.warn("[DebugSession {}] trace 文件自动清理异常，fileId={}", sessionId, fileToDelete, e);
                }
            }, TRACE_AUTO_DELETE_MINUTES, TimeUnit.MINUTES);
    
        } catch (Exception e) {
            log.warn("[DebugSession {}] tracing.stop / 上传失败，跳过 trace 回放", sessionId, e);
            if (tracePath != null) {
                try { Files.deleteIfExists(tracePath); } catch (Exception ignored) {}
            }
        }
    }
    
    
    /**
     * \u542f\u52a8\u8c03\u8bd5\u4f1a\u8bdd
     *
     * @param sessionId
     * @return
     */
    public boolean startSession(String sessionId) {
        this.sessionId = sessionId;
        if (state != DebuggerState.READY) {
            System.out.println("Debug session already started or finished");
            return false;
        }
        state = DebuggerState.RUNNING;
        executionThread = new Thread(this::executeTest);
        executionThread.start();
        return true;
    }

    /**
     * 等待用户重试或挂起超时。
     * 收到 RETRY 命令后从数据库重新拉取步骤并进入 RUNNING 状态；
     * 超时时设置 FAILED 并返回 false；QUIT 时设置 FINISHED 并返回 false。
     *
     * @return true 表示可以继续重试，false 表示结束执行
     */
    private boolean waitForRetryOrTimeout() {
        String message = String.format("PAUSED_ON_FAILURE:%d:%d", failedStepId, failurePauseDeadlineMs);
        log.info("[DebugSession {}] 进入 PAUSED_ON_FAILURE，failedStepId={}, deadlineMs={}",
                this.sessionId, failedStepId, failurePauseDeadlineMs);
        DebugWebSocketHandler.sendToSession(this.sessionId, GSON.toJson(message));

        while (state == DebuggerState.PAUSED_ON_FAILURE) {
            try {
                String command = commandQueue.poll(1, TimeUnit.SECONDS);
                if (command != null) {
                    log.info("[DebugSession {}] 挂起期间收到命令: {}", this.sessionId, command);
                    handleCommand(command);
                }

                if (state == DebuggerState.FINISHED) {
                    log.info("[DebugSession {}] 收到退出命令，结束挂起", this.sessionId);
                    return false;
                }

                if (state == DebuggerState.RUNNING) {
                    // 收到重试命令，重新加载步骤
                    log.info("[DebugSession {}] 收到重试命令，准备重试", this.sessionId);
                    return prepareRetry();
                }

                if (System.currentTimeMillis() >= failurePauseDeadlineMs) {
                    state = DebuggerState.FAILED;
                    long timeoutMinutes = FAILURE_PAUSE_TIMEOUT_MS / 60 / 1000;
                    log.info("[DebugSession {}] 失败挂起超时（{} 分钟），自动关闭浏览器", this.sessionId, timeoutMinutes);
                    DebugWebSocketHandler.sendToSession(this.sessionId,
                            GSON.toJson("RETRY_FAILED: 挂起超时，浏览器即将关闭"));
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                state = DebuggerState.FAILED;
                return false;
            }
        }
        log.info("[DebugSession {}] 挂起状态被改为 {}，退出等待", this.sessionId, this.state);
        return false;
    }

    /**
     * 从数据库重新加载场景步骤，并截取到失败步骤所在顶层节点往后的步骤。
     *
     * @return true 表示准备成功
     */
    private boolean prepareRetry() {
        try {
            log.info("[DebugSession {}] 开始准备重试，原失败 stepId={}", this.sessionId, this.failedStepId);
            StepBuilder stepBuilder = ApplicationContextHolder.getBean(StepBuilder.class);
            List<StepTreeNode> newTree = stepBuilder.buildScenarioSteps(this.getScenarioId());
            if (newTree == null || newTree.isEmpty()) {
                state = DebuggerState.FAILED;
                DebugWebSocketHandler.sendToSession(this.sessionId,
                        GSON.toJson("RETRY_FAILED: 场景步骤为空"));
                return false;
            }

            List<StepTreeNode> path = findPathToStep(newTree, this.failedStepId);
            if (path == null || path.isEmpty()) {
                state = DebuggerState.FAILED;
                log.warn("[DebugSession {}] 重试时找不到失败步骤 id={}", this.sessionId, this.failedStepId);
                DebugWebSocketHandler.sendToSession(this.sessionId,
                        GSON.toJson("RETRY_FAILED: 失败步骤已被删除或变更，无法继续重试"));
                return false;
            }

            // 以失败步骤的顶层祖先作为重试起点，确保循环/条件等容器步骤可完整重新执行
            StepTreeNode topLevelAncestor = path.get(0);
            int ancestorIndex = -1;
            for (int j = 0; j < newTree.size(); j++) {
                if (newTree.get(j).getStepEntity().getId().equals(topLevelAncestor.getStepEntity().getId())) {
                    ancestorIndex = j;
                    break;
                }
            }
            if (ancestorIndex < 0) {
                state = DebuggerState.FAILED;
                DebugWebSocketHandler.sendToSession(this.sessionId,
                        GSON.toJson("RETRY_FAILED: 无法定位失败步骤位置"));
                return false;
            }

            List<StepTreeNode> retrySteps = newTree.subList(ancestorIndex, newTree.size());
            this.testSteps.clear();
            this.testSteps.addAll(retrySteps);
            this.stepResults = initialStepResults(this.testSteps);
            // 重置继续执行标志，确保重试时步骤不会被旧状态跳过
            this.isContinue = true;
            log.info("[DebugSession {}] 重试准备完成，共 {} 个顶层步骤，从 stepId={} 开始",
                    this.sessionId, this.testSteps.size(), topLevelAncestor.getStepEntity().getId());
            return true;
        } catch (Exception e) {
            state = DebuggerState.FAILED;
            DebugWebSocketHandler.sendToSession(this.sessionId,
                    GSON.toJson("RETRY_FAILED: " + e.getMessage()));
            log.error("[DebugSession {}] prepareRetry 异常", this.sessionId, e);
            return false;
        }
    }

    /**
     * 在步骤树中查找指定 stepId 的节点路径（从顶层节点到目标节点）。
     *
     * @param tree   顶层步骤树
     * @param stepId 目标步骤 id
     * @return 路径节点列表，找不到返回 null
     */
    private List<StepTreeNode> findPathToStep(List<StepTreeNode> tree, Integer stepId) {
        for (StepTreeNode node : tree) {
            List<StepTreeNode> path = findPathRecursive(node, stepId);
            if (path != null) {
                return path;
            }
        }
        return null;
    }

    private List<StepTreeNode> findPathRecursive(StepTreeNode node, Integer stepId) {
        if (node.getStepEntity().getId().equals(stepId)) {
            List<StepTreeNode> path = new ArrayList<>();
            path.add(node);
            return path;
        }
        if (node.getChildren() != null) {
            for (StepTreeNode child : node.getChildren()) {
                List<StepTreeNode> childPath = findPathRecursive(child, stepId);
                if (childPath != null) {
                    List<StepTreeNode> path = new ArrayList<>();
                    path.add(node);
                    path.addAll(childPath);
                    return path;
                }
            }
        }
        return null;
    }

    /**
     * 在当前步骤列表中定位重试起点。
     * 优先返回失败步骤本身的顶层索引；若失败步骤是子步骤，则返回其顶层祖先的索引，
     * 确保循环/条件容器可以完整重新执行。
     *
     * @param stepId 失败步骤 id
     * @return 重试起点在顶层步骤列表中的索引，找不到返回 -1
     */
    private int findRetryStartIndex(Integer stepId) {
        List<StepTreeNode> path = findPathToStep(this.testSteps, stepId);
        if (path == null || path.isEmpty()) {
            return -1;
        }
        StepTreeNode topLevelAncestor = path.get(0);
        for (int i = 0; i < this.testSteps.size(); i++) {
            if (this.testSteps.get(i).getStepEntity().getId().equals(topLevelAncestor.getStepEntity().getId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 调试命令检查
     *
     * @param pendingStep 调用本检查的待执行步骤（暂停续跑热加载的优先锚点）
     */
    public void checkDebugCommands(AbstractTestStep pendingStep) throws InterruptedException {
        if (state == DebuggerState.FINISHED || state == DebuggerState.FAILED) {
            return;
        }

        // 非阻塞检查命令队列
        String command = null;
        while ((command = commandQueue.poll()) != null) {
            handleCommand(command);
            // 如果收到退出命令，终止测试
            if (state == DebuggerState.FINISHED || state == DebuggerState.FAILED) {
                return;
            }
        }

        // 「执行到此步骤」：目标步骤已执行完成，在下一个步骤执行前自动暂停
        // （目标步骤的 execute 在 finally 中通过 markReachedIfTarget 打上标记）
        if (pauseAfterTargetReached) {
            log.info("[DebugSession {}] 目标步骤已执行完成，自动暂停", this.sessionId);
            pauseAfterTargetReached = false;
            state = DebuggerState.PAUSED;
        }

        // 如果收到暂停命令，进入暂停状态等待
        if (state == DebuggerState.PAUSED) {
            long startWaitTime = System.currentTimeMillis();
            long timeoutMillis = getPauseTimeoutMillis();
            long pauseDeadlineMs = startWaitTime + timeoutMillis;
            log.info("[DebugSession {}] 进入 PAUSED，暂停超时 {} ms，deadlineMs={}",
                    this.sessionId, timeoutMillis, pauseDeadlineMs);
            DebugWebSocketHandler.sendToSession(this.sessionId,
                    GSON.toJson(String.format("PAUSED:%d", pauseDeadlineMs)));

            // 等待继续命令或超时
            while (state == DebuggerState.PAUSED) {
                try {
                    // 等待最多1秒检查一次命令，避免长时间阻塞
                    command = commandQueue.poll(1, TimeUnit.SECONDS);
                    if (command != null) {
                        log.info("[DebugSession {}] 暂停期间收到命令: {}", this.sessionId, command);
                        handleCommand(command);
                    }

                    // 检查超时
                    if (System.currentTimeMillis() - startWaitTime > timeoutMillis) {
                        long timeoutMinutes = timeoutMillis / 60 / 1000;
                        log.info("[DebugSession {}] 调试暂停超时（{} 分钟），自动关闭浏览器", this.sessionId, timeoutMinutes);
                        state = DebuggerState.FINISHED;
                        break;
                    }

                    // 如果状态变成FINISHED的时候，直接打断线程
                    if (state == DebuggerState.FINISHED) {
                        break;
                    }

                    // 如果状态改变（如收到继续命令），退出等待循环
                    if (state != DebuggerState.PAUSED) {
                        break;
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    state = DebuggerState.FAILED;
                    throw e;
                }
            }

            // 暂停结束且用户选择继续：检测暂停期间步骤是否变更，有变更则热加载并回退执行栈
            if (state == DebuggerState.RUNNING) {
                prepareResume(pendingStep);
            }
        }
    }

    /**
     * 暂停后继续执行前的热加载处理。
     * 从数据库重新拉取步骤并与当前内存快照对比：
     * - 无变更：直接返回，执行栈按原位置继续（容器内暂停位置完全保留）；
     * - 有变更：定位续跑锚点（待执行步骤的顶层祖先；待执行步骤被删则回退到最后执行步骤的顶层祖先），
     *   截断步骤列表并抛出 {@link DebugSessionReloadException}，
     *   由 executeTest 顶层捕获后从锚点重新执行（循环/条件容器整体重跑，与失败重试语义一致）。
     *
     * @param pendingStep 暂停时待执行的步骤
     */
    private void prepareResume(AbstractTestStep pendingStep) {
        try {
            StepBuilder stepBuilder = ApplicationContextHolder.getBean(StepBuilder.class);
            List<StepTreeNode> newTree = stepBuilder.buildScenarioSteps(this.getScenarioId());
            if (newTree == null) {
                newTree = new ArrayList<>();
            }

            // 快路径：暂停期间步骤无任何变更，按原位置继续，不重载
            if (GSON.toJson(newTree).equals(GSON.toJson(this.testSteps))) {
                log.info("[DebugSession {}] 暂停期间步骤无变更，按原位置继续执行", this.sessionId);
                return;
            }

            // 计算续跑锚点：优先定位待执行步骤的顶层祖先
            Integer anchorStepId = pendingStep != null ? pendingStep.getId() : null;
            List<StepTreeNode> path = anchorStepId != null ? findPathToStep(newTree, anchorStepId) : null;
            if (path == null || path.isEmpty()) {
                // 待执行步骤在暂停期间被删除：回退到最后开始执行步骤的顶层祖先
                log.info("[DebugSession {}] 待执行步骤 {} 已被删除，回退到最后执行步骤 {}",
                        this.sessionId, anchorStepId, this.lastExecutedStepId);
                path = this.lastExecutedStepId != null ? findPathToStep(newTree, this.lastExecutedStepId) : null;
            }

            int resumeIndex = -1;
            if (path != null && !path.isEmpty()) {
                StepTreeNode topLevelAncestor = path.get(0);
                for (int j = 0; j < newTree.size(); j++) {
                    if (newTree.get(j).getStepEntity().getId().equals(topLevelAncestor.getStepEntity().getId())) {
                        resumeIndex = j;
                        break;
                    }
                }
            }
            if (resumeIndex < 0) {
                log.warn("[DebugSession {}] 无法定位续跑锚点，从头开始执行", this.sessionId);
                resumeIndex = 0;
            }

            this.testSteps.clear();
            this.testSteps.addAll(new ArrayList<>(newTree.subList(resumeIndex, newTree.size())));
            this.stepResults = initialStepResults(this.testSteps);
            // 重置继续执行标志，确保续跑时步骤不会被旧状态跳过
            this.isContinue = true;
            this.hotReloadInProgress = true;
            log.info("[DebugSession {}] 步骤已变更，热加载后从顶层下标 {} 续跑，共 {} 个顶层步骤",
                    this.sessionId, resumeIndex, this.testSteps.size());
            DebugWebSocketHandler.sendToSession(this.sessionId, GSON.toJson("STEPS_RELOADED"));
            throw new DebugSessionReloadException("步骤已变更，从锚点步骤重新执行");
        } catch (DebugSessionReloadException e) {
            throw e;
        } catch (Exception e) {
            // 热加载失败时按原步骤继续执行，不中断调试
            this.hotReloadInProgress = false;
            log.error("[DebugSession {}] prepareResume 异常，按原步骤继续执行", this.sessionId, e);
        }
    }

    /**
     * 获取调试暂停超时时间（毫秒）。
     * 暂停超时时间已统一由后端固定为 5 分钟，不再读取前端场景配置。
     *
     * @return 暂停超时毫秒数
     */
    private long getPauseTimeoutMillis() {
        return 5 * 60 * 1000L;
    }


}