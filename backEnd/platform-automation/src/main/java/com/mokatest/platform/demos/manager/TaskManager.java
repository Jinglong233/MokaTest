package com.mokatest.platform.demos.manager;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.api.service.ApiPlanResultConverter;
import com.mokatest.platform.demos.api.service.ApiSceneDebugService;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.uiEnum.plan.PlanCategory;
import com.mokatest.platform.demos.domain.ui.uiEnum.task.SceneExecuteType;
import com.mokatest.platform.demos.domain.ui.uiEnum.task.TaskExecuteStatus;
import com.mokatest.platform.demos.debug.PlaywrightPlanSession;
import com.mokatest.platform.demos.domain.ui.*;
import com.mokatest.platform.demos.mapper.*;
import com.mokatest.platform.demos.service.WebhookNotifyService;
import com.mokatest.platform.demos.step.Node.StepBuilder;
import com.mokatest.platform.demos.step.Node.StepTreeNode;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
public class TaskManager {

    private final ThreadPoolTaskScheduler scheduler;
    /** 调度任务映射：planId → ScheduledFuture，用于管理定时任务的调度生命周期 */
    private final Map<Integer, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();

    /**
     * 立即执行（含手动点击"立即执行"、报告详情页"重试失败场景"）的运行状态。
     * 与 scheduledRunningMap 分离，确保手动执行和定时任务互不干扰。
     */
    private final Map<Integer, Boolean> immediateRunningMap = new ConcurrentHashMap<>();

    /**
     * 定时任务的运行状态。
     * 当定时任务的 Runnable 实际开始执行时置为 true，执行完毕后置为 false。
     */
    private final Map<Integer, Boolean> scheduledRunningMap = new ConcurrentHashMap<>();

    /**
     * 每个计划ID对应的互斥锁，用于防止定时任务重叠执行。
     * 当定时任务执行时间超过 Cron 间隔时，调度器会提交新的 Runnable，
     * 通过 tryLock 可检测上次执行是否完成，未完成则跳过本次调度。
     */
    private final Map<Integer, ReentrantLock> planLocks = new ConcurrentHashMap<>();

    /**
     * 场景并行执行线程池（全局复用）。
     * 避免每次执行计划都新建/销毁线程池的开销，核心数与最大并发数一致。
     */
    private final ExecutorService planSceneExecutor = new ThreadPoolExecutor(
            10, 10,
            60L, TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r);
                t.setName("plan-scene-" + t.getId());
                t.setDaemon(true);
                return t;
            });


    @Resource
    private PlanMapper planMapper;

    @Resource
    private SceneMapper sceneMapper;

    @Resource
    private TestStepMapper testStepMapper;


    @Resource
    private ReportMapper reportMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private StepBuilder stepBuilder;

    @Resource
    private ApiSceneDebugService apiSceneDebugService;

    @Resource
    private com.mokatest.platform.demos.api.service.SceneEnvironmentSupport sceneEnvironmentSupport;

    @Resource
    private ApiPlanResultConverter apiPlanResultConverter;

    /**
     * Webhook 通知发送服务
     * 
     * 在计划执行完成后，根据配置向第三方平台发送通知。
     * 注入此服务不影响原有任务调度逻辑，发送为异步独立流程，异常被捕获不抛出。
     */
    @Resource
    private WebhookNotifyService webhookNotifyService;


    public TaskManager() {
        this.scheduler = new ThreadPoolTaskScheduler();
        this.scheduler.setPoolSize(5);
        this.scheduler.setThreadNamePrefix("simple-task-");
        this.scheduler.initialize();
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭任务调度器...");
        for (Map.Entry<Integer, ScheduledFuture<?>> entry : taskMap.entrySet()) {
            ScheduledFuture<?> future = entry.getValue();
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        }
        taskMap.clear();
        // 清理两种执行类型的运行状态，防止应用重启后残留脏数据
        immediateRunningMap.clear();
        scheduledRunningMap.clear();
        // 清理互斥锁，避免重启后锁对象残留导致不可预期的行为
        planLocks.clear();
        scheduler.shutdown();
        // 关闭场景并行执行线程池
        planSceneExecutor.shutdown();
        try {
            if (!planSceneExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                planSceneExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            planSceneExecutor.shutdownNow();
        }
        log.info("任务调度器已关闭");
    }

    /**
     * 添加或更新任务
     */
    public Boolean addTask(Plan plan) {
        try {
            Integer planId = plan.getId();
            // 先停止现有任务
            stopTask(planId);

            Object status = plan.getStatus();
            // 只有是未开始的才执行计划
            if (TaskExecuteStatus.NOT_STARTED.equals(TaskExecuteStatus.valueOf(status.toString()))) {
                // 先更新计划状态
                plan.setStatus(TaskExecuteStatus.IN_PROGRESS);
                planMapper.updateById(plan);

                String cronExpression = plan.getCronExpression();
                if (plan.getParams() == null) {
                    throw new RuntimeException("请选择需要执行的场景");
                }
                String s = plan.getParams().toString();
                if (s == null || new Gson().fromJson(s, List.class).isEmpty()) {
                    throw new RuntimeException("请选择需要执行的场景");
                }
                Type type = new TypeToken<List<Integer>>() {
                }.getType();
                List<Integer> scenesList = new Gson().fromJson(s, type);
                // 创建新任务
                // 防重复：只检查定时任务是否正在执行，不拦截立即执行。
                // 因为 immediateRunningMap 和 scheduledRunningMap 已按类型拆分，
                // 手动点击"立即执行"不会阻塞定时任务的调度，反之亦然。
                if (scheduledRunningMap.containsKey(planId) && scheduledRunningMap.get(planId)) {
                    throw new RuntimeException("计划定时任务正在执行，请等待完成后再激活");
                }
                Runnable task = createTask(planId, scenesList,
                        plan.getCreateUserId() != null ? String.valueOf(plan.getCreateUserId()) : "system");
                CronTrigger trigger = new CronTrigger(cronExpression);
                ScheduledFuture<?> future = scheduler.schedule(task, trigger);
                taskMap.put(planId, future);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException("添加或更新任务失败: " + e.getMessage());
        }
    }


    /**
     * 更新任务
     * 如果对应的计划在调度队列中，则更新这个计划的相关信息
     * 不会影响当前正在执行的任务，从下一次执行开始使用新配置
     *
     * @param plan 要更新的计划
     * @return 更新是否成功
     */
    public Boolean updateTask(Plan plan) {
        try {
            Integer planId = plan.getId();

            // 检查任务是否在调度队列中
            if (taskMap.containsKey(planId)) {
                String cronExpression = plan.getCronExpression();
                if (plan.getParams() == null) {
                    throw new RuntimeException("请选择需要执行的场景");
                }
                String s = plan.getParams().toString();
                if (s == null || new Gson().fromJson(s, List.class).isEmpty()) {
                    throw new RuntimeException("请选择需要执行的场景");
                }
                Type type = new TypeToken<List<Integer>>() {
                }.getType();
                List<Integer> scenesList = new Gson().fromJson(s, type);
                // 创建新任务
                Runnable task = createTask(planId, scenesList,
                        plan.getCreateUserId() != null ? String.valueOf(plan.getCreateUserId()) : "system");
                CronTrigger trigger = new CronTrigger(cronExpression);

                // 先取消旧的任务，但不中断正在执行的任务
                ScheduledFuture<?> oldFuture = taskMap.get(planId);
                if (oldFuture != null) {
                    oldFuture.cancel(false);
                }

                // 调度新任务
                ScheduledFuture<?> future = scheduler.schedule(task, trigger);

                taskMap.put(planId, future);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException("更新任务失败: " + e.getMessage());
        }
    }

    /**
     * 停止任务（安全方式）
     */
    public Boolean stopTask(Integer taskId) {
        ScheduledFuture<?> future = taskMap.get(taskId);
        if (future != null) {
            future.cancel(false); // 不中断正在执行的任务
            taskMap.remove(taskId);
            // 停止调度时同时清理两种执行类型的状态标记
            immediateRunningMap.remove(taskId);
            scheduledRunningMap.remove(taskId);
            return true;
        }
        return false;
    }

    /**
     * 立即执行一次任务
     * 同步创建报告并返回报告ID，场景执行异步进行
     */
    public Integer executeNow(Integer taskId) {
        Plan plan = planMapper.selectById(taskId);
        if (plan == null) {
            throw new RuntimeException("任务不存在");
        }
        if (plan.getParams() == null) {
            throw new RuntimeException("请选择需要执行的场景");
        }

        // 防重复执行：只检查【立即执行】类型。
        // 如果该计划已有立即执行（含重试失败场景）正在跑，则返回现有 reportId，
        // 前端 reopen 抽屉继续查看，不会创建新报告。
        // 注意：此处不检查 scheduledRunningMap，定时任务和立即执行互不干扰。
        if (immediateRunningMap.getOrDefault(taskId, false)) {
            QueryWrapper<Report> qw = new QueryWrapper<>();
            qw.eq("plan_id", taskId).eq("status", 0)
                    .orderByDesc("create_time").last("limit 1");
            Report runningReport = reportMapper.selectOne(qw);
            if (runningReport != null) {
                log.warn("计划[{}]立即执行正在运行中，拒绝重复执行，返回现有报告ID: {}", taskId, runningReport.getId());
                return runningReport.getId();
            }
        }

        Type listType = new TypeToken<List<Integer>>() {
        }.getType();
        List<Integer> scenesList = new Gson().fromJson(plan.getParams().toString(), listType);

        String executionUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsString()
                : (plan.getCreateUserId() != null ? String.valueOf(plan.getCreateUserId()) : "system");

        // 同步创建报告，确保前端立即可见
        Report report = initReport(taskId, scenesList, executionUserId);
        reportMapper.insert(report);

        // 异步执行场景
        scheduler.submit(createTask(taskId, scenesList, executionUserId, report.getId()));
        return report.getId();
    }

    /**
     * 创建任务Runnable
     */
    private Runnable createTask(Integer taskId, List<Integer> list, String executionUserId) {
        return createTask(taskId, list, executionUserId, null);
    }

    /**
     * 创建任务 Runnable。
     *
     * @param preCreatedReportId 预创建的报告ID。
     *                           非 null 表示【立即执行】（executeNow / 重试），使用 immediateRunningMap；
     *                           null 表示【定时任务】，使用 scheduledRunningMap。
     */
    private Runnable createTask(Integer taskId, List<Integer> list, String executionUserId, Integer preCreatedReportId) {
        // 根据 preCreatedReportId 判断执行类型，选择对应的状态 Map
        boolean isImmediate = preCreatedReportId != null;
        Map<Integer, Boolean> targetRunningMap = isImmediate ? immediateRunningMap : scheduledRunningMap;

        // 定时任务需要互斥锁防止重叠执行；立即执行由前端/executeNow防重，不需要锁
        ReentrantLock lock = isImmediate ? null : planLocks.computeIfAbsent(taskId, k -> new ReentrantLock());

        return () -> {
            // 记录 Runnable 真正开始执行的时刻，用于准确计算执行时长（不含 scheduler 排队时间）
            long taskStartTime = System.currentTimeMillis();
            // 定时任务：检查是否已有实例在执行，有则跳过本次调度
            if (!isImmediate && lock != null) {
                if (!lock.tryLock()) {
                    log.warn("计划[{}]的定时任务上次执行尚未完成，跳过本次调度", taskId);
                    return;
                }
            }
            targetRunningMap.put(taskId, true);
            Report report;
            if (preCreatedReportId != null) {
                // executeNow 场景：报告已同步创建，直接查询使用
                report = reportMapper.selectById(preCreatedReportId);
                if (report == null) {
                    throw new RuntimeException("报告不存在或已被删除，reportId=" + preCreatedReportId);
                }
            } else {
                // 定时任务场景：需要创建报告
                report = initReport(taskId, list, executionUserId);
                int insert = reportMapper.insert(report);
                if (insert <= 0) {
                    throw new RuntimeException("计划初始化失败");
                }
            }
            // 统一收集所有场景的结果，最后在 finally 中一次性写入报告
            java.util.Map<String, java.util.List<com.mokatest.platform.demos.result.StepResult>> allScenesResults = new java.util.concurrent.ConcurrentHashMap<>();
            try {
                Plan plan = planMapper.selectById(taskId);
                if (plan == null) {
                    throw new RuntimeException("计划不存在");
                }
                SceneExecuteType sceneExecuteType = SceneExecuteType.valueOf(plan.getExecutionType().toString());
                String planCategory = plan.getPlanCategory() == null || plan.getPlanCategory().isBlank()
                        ? PlanCategory.UI.name() : plan.getPlanCategory();
                if (PlanCategory.API.name().equals(planCategory)) {
                    executeApiPlan(plan, list, report, sceneExecuteType, allScenesResults);
                } else {
                    String authContext = null;
                    // 执行前置的认证内容
                if (plan.getSceneStatusExtract() != null) {
                    PlaywrightPlanSession playwrightPlanSession = new PlaywrightPlanSession();
                    playwrightPlanSession.setPlan(plan);
                    playwrightPlanSession.setReportId(report.getId());
                    Scene scene = sceneMapper.selectById(plan.getSceneStatusExtract());
                    if (scene != null) {
                        playwrightPlanSession.setAuthStep(stepBuilder.buildScenarioSteps(plan.getSceneStatusExtract()));
                        initSceneInfo(playwrightPlanSession, taskId, plan.getSceneStatusExtract());
                    }
                    // 先执行认证场景
                    playwrightPlanSession.startAuthExecute();
                    // 获取认证内容
                    authContext = playwrightPlanSession.getAuthContext();
                }
                if (sceneExecuteType == SceneExecuteType.ORDER) {
                    // 将计划执行session对象提取出来，避免每次执行场景都初始化session对象，节省资源
                    // 目前是：同一个浏览器，多个上下文
                    PlaywrightPlanSession playwrightPlanSession = new PlaywrightPlanSession();
                    playwrightPlanSession.setPlan(plan);
                    playwrightPlanSession.setReportId(report.getId());
                    // 根据场景id处理构建场景
                    for (Integer sceneId : list) {
                        // 判断sceneId是否存在
                        Scene scene = sceneMapper.selectById(sceneId);
                        if (scene == null) {
                            // todo 考虑删除计划里边不存在的场景
                            continue;
                        }
                        playwrightPlanSession.setScenarioId(sceneId);
                        playwrightPlanSession.setSceneName(scene.getName());
                        if (authContext != null) {
                            playwrightPlanSession.setAuthContext(authContext);
                        }
                        List<StepTreeNode> stepTreeNodes = stepBuilder.buildScenarioSteps(sceneId);
                        // 每次需要清空步骤列表
                        playwrightPlanSession.getTestSteps().clear();
                        stepTreeNodes.forEach(playwrightPlanSession::addStep);
                        initSceneInfo(playwrightPlanSession, taskId, sceneId);
                        // 等待上一个场景执行完之后在开始执行下一个
                        playwrightPlanSession.startPlanAndWait(taskId);
                        // 场景执行完后立即写库，确保前端轮询能看到该场景的完整结果
                        playwrightPlanSession.updateReportSceneResult();
                        // 收集当前场景结果
                        java.util.List<com.mokatest.platform.demos.result.StepResult> sceneResults = playwrightPlanSession.getSceneStepResults();
                        if (sceneResults != null) {
                            allScenesResults.put(sceneId + "_" + scene.getName(), new java.util.ArrayList<>(sceneResults));
                        }
                    }
                    // 串行模式所有场景执行完后，关闭浏览器和Playwright资源
                    playwrightPlanSession.closeAllResources();
                } else if (sceneExecuteType == SceneExecuteType.PARALLEL) {
                    // 创建一个列表来存储所有的session，以便等待它们完成
                    List<PlaywrightPlanSession> sessions = new ArrayList<>();
                    // 复用全局场景执行线程池，避免每次执行计划都新建/销毁线程池
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(list.size());
                    for (Integer sceneId : list) {
                        // 判断sceneId是否存在
                        Scene scene = sceneMapper.selectById(sceneId);
                        if (scene == null) {
                            // todo 考虑删除计划里边不存在的场景
                            latch.countDown(); // 场景不存在也要减计数，防止死等
                            continue;
                        }

                        PlaywrightPlanSession playwrightPlanSession = new PlaywrightPlanSession();
                        playwrightPlanSession.setPlan(plan);
                        playwrightPlanSession.setReportId(report.getId());
                        playwrightPlanSession.setScenarioId(sceneId);
                        playwrightPlanSession.setSceneName(scene.getName());
                        // 停0.5s
                        Thread.sleep(500);
                        if (authContext != null) {
                            playwrightPlanSession.setAuthContext(authContext);
                        }

                        List<StepTreeNode> stepTreeNodes = stepBuilder.buildScenarioSteps(sceneId);
                        stepTreeNodes.forEach(playwrightPlanSession::addStep);
                        initSceneInfo(playwrightPlanSession, taskId, sceneId);
                        sessions.add(playwrightPlanSession);

                        // 提交任务到全局线程池执行
                        planSceneExecutor.submit(() -> {
                            try {
                                playwrightPlanSession.executeTest();
                            } catch (Exception e) {
                                log.error("场景[{}]执行异常", scene.getName(), e);
                            } finally {
                                // 收集当前场景结果
                                java.util.List<com.mokatest.platform.demos.result.StepResult> sceneResults = playwrightPlanSession.getSceneStepResults();
                                if (sceneResults != null) {
                                    allScenesResults.put(sceneId + "_" + scene.getName(), new java.util.ArrayList<>(sceneResults));
                                }
                                latch.countDown(); // 任务完成，计数减1
                            }
                        });
                    }
                    // 等待所有任务完成
                    try {
                        boolean allDone = latch.await(60, TimeUnit.MINUTES);
                        if (!allDone) {
                            log.error("部分任务执行超时（60分钟），强制终止剩余任务");
                            // 先标记，让 worker 在步骤间隙主动退出，避免访问已关闭的资源
                            for (PlaywrightPlanSession session : sessions) {
                                session.setForceTerminated(true);
                            }
                            // 再关闭资源
                            for (PlaywrightPlanSession session : sessions) {
                                session.closeAllResources();
                            }
                        }
                    } catch (InterruptedException e) {
                        log.error("等待任务完成时被中断", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }

            } catch (Exception e) {
                e.printStackTrace();
                log.error("任务：{}执行异常，异常详情:{}: " + Thread.currentThread().getName(),e.getMessage());
            } finally {
                // 统一收集所有场景结果并写入报告
                Report report1 = reportMapper.selectById(report.getId());
                Gson gson = new Gson();
                report1.setScenes(gson.toJson(allScenesResults));

                // 统计步骤/断言数量并写入报告
                fillReportStats(report1, allScenesResults, taskStartTime);

                // 根据本次报告结果更新项目 UI 通过率（仅 UI 报告）
                if (PlanCategory.UI.name().equals(report1.getReportCategory())) {
                    try {
                        Integer reportProjectId = report1.getProjectId();
                        Integer stepNumber = report1.getStepNumber();
                        if (reportProjectId != null && stepNumber != null && stepNumber > 0) {
                            int stepSuccessNumber = report1.getStepSuccessNumber() == null ? 0 : report1.getStepSuccessNumber();
                            int uiPass = stepSuccessNumber * 100 / stepNumber;
                            Project project = new Project();
                            project.setId(reportProjectId);
                            project.setUiPass(uiPass);
                            projectMapper.updateById(project);
                        }
                    } catch (Exception e) {
                        log.error("更新项目 UI 通过率失败, projectId={}, reportId={}", report1.getProjectId(), report1.getId(), e);
                    }
                }

                // ===== Webhook 通知发送（新增，不影响原有逻辑）=====
                // 发送失败仅记录日志，绝不抛异常影响主流程
                try {
                    if (webhookNotifyService != null) {
                        // plan 在 try 块内定义，finally 中重新查询一次，避免改原有变量作用域
                        Plan notifyPlan = planMapper.selectById(taskId);
                        if (notifyPlan != null) {
                            webhookNotifyService.send(notifyPlan, report1);
                        }
                    }
                } catch (Exception e) {
                    log.error("Webhook 通知发送异常，planId={}", taskId, e);
                }

                // 执行完毕后，将对应类型的运行状态置为 false
                targetRunningMap.put(taskId, false);
                // 定时任务：释放互斥锁，允许下一次调度进入
                if (!isImmediate && lock != null) {
                    lock.unlock();
                }
            }
        };
    }


    private void initSceneInfo(PlaywrightPlanSession playwrightPlanSession, Integer planId, Integer sceneId) {
        Plan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("计划不存在");
        }
        String planRunningSetting = plan.getPlanRunningSetting();
        playwrightPlanSession.setSceneExecuteType(SceneExecuteType.valueOf(plan.getExecutionType().toString()));
        playwrightPlanSession.setSceneSetting(new Gson().fromJson(planRunningSetting, SceneSetting.class));
        // 环境取每个场景自己的 sceneSetting（apiSceneConfig.environmentId，与调试链路一致），
        // planRunningSetting 是计划级浏览器配置，不含环境；注入共享变量池供 UI/HTTP/SQL 步骤使用
        Scene scene = sceneMapper.selectById(sceneId);
        if (scene != null) {
            SceneSetting sceneOwnSetting = new Gson().fromJson(scene.getSceneSetting(), SceneSetting.class);
            com.mokatest.platform.demos.api.domain.Environment sceneEnv =
                    sceneEnvironmentSupport.resolveSceneEnvironment(sceneOwnSetting);
            playwrightPlanSession.setSceneEnvironment(sceneEnv);
            sceneEnvironmentSupport.injectEnvironmentVariables(
                    playwrightPlanSession.getVariables(), sceneEnv, scene.getProjectId());
        }
    }

    /**
     * 执行 API 计划
     *
     * @param plan              计划对象
     * @param sceneIds          场景 ID 列表
     * @param report            预创建的报告
     * @param sceneExecuteType  执行方式（串行/并行）
     * @param allScenesResults  结果收集容器
     */
    private void executeApiPlan(Plan plan, List<Integer> sceneIds, Report report,
                                SceneExecuteType sceneExecuteType,
                                java.util.Map<String, java.util.List<com.mokatest.platform.demos.result.StepResult>> allScenesResults) {
        if (sceneExecuteType == SceneExecuteType.ORDER) {
            for (Integer sceneId : sceneIds) {
                Scene scene = sceneMapper.selectById(sceneId);
                if (scene == null) {
                    log.warn("API 计划中存在不存在的场景, sceneId={}", sceneId);
                    continue;
                }
                try {
                    ApiSceneDebugService.ApiSceneDebugResult debugResult = apiSceneDebugService.debugScene(sceneId);
                    List<com.mokatest.platform.demos.result.StepResult> stepResults =
                            apiPlanResultConverter.convert(sceneId, debugResult.getStepResults());
                    allScenesResults.put(sceneId + "_" + scene.getName(), stepResults);
                } catch (Exception e) {
                    log.error("API 场景执行异常, sceneId={}", sceneId, e);
                }
            }
        } else {
            // 并行模式
            java.util.concurrent.ExecutorService executorService =
                    java.util.concurrent.Executors.newFixedThreadPool(Math.min(sceneIds.size(), 5));
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(sceneIds.size());
            for (Integer sceneId : sceneIds) {
                Scene scene = sceneMapper.selectById(sceneId);
                if (scene == null) {
                    latch.countDown();
                    continue;
                }
                executorService.submit(() -> {
                    try {
                        ApiSceneDebugService.ApiSceneDebugResult debugResult = apiSceneDebugService.debugScene(sceneId);
                        List<com.mokatest.platform.demos.result.StepResult> stepResults =
                                apiPlanResultConverter.convert(sceneId, debugResult.getStepResults());
                        allScenesResults.put(sceneId + "_" + scene.getName(), stepResults);
                    } catch (Exception e) {
                        log.error("API 场景执行异常, sceneId={}", sceneId, e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            try {
                boolean allDone = latch.await(60, TimeUnit.MINUTES);
                if (!allDone) {
                    log.error("部分 API 场景执行超时（60分钟），强制终止剩余任务");
                    executorService.shutdownNow();
                    try {
                        if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                            log.error("API 场景线程池未能在30秒内终止");
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    executorService.shutdown();
                }
            } catch (InterruptedException e) {
                log.error("等待 API 场景完成时被中断", e);
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            }
        }
    }

    /**
     * 初始化报告
     *
     * @return
     */
    private Report initReport(Integer planId, List<Integer> list, String executionUserId) {
        // 查询计划信息
        Plan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new RuntimeException("计划不存在");
        }
        // 创建关联的报告
        Report report = new Report();
        report.setCreateTime(new Date());
        report.setExecutionUserId(executionUserId);
        // 设置报告所属项目id
        report.setProjectId(plan.getProjectId());
        Integer sceneNumber = 0;
        Integer stepNumber = 0;
        Integer assertNumber = 0;
        for (Integer sceneId : list) {
            Scene scene = sceneMapper.selectById(sceneId);
            if (scene != null) {
                sceneNumber++;
                // 获取步骤的数量
                stepNumber += Integer.valueOf(testStepMapper.selectCount(new QueryWrapper<TestStep>().eq("scenario_id"
                        , sceneId)).toString()).intValue();
                // 获取断言步骤的数量
                QueryWrapper<TestStep> testStepQueryWrapper = new QueryWrapper<>();
                testStepQueryWrapper.eq("scenario_id", sceneId);
                List<TestStep> testSteps = testStepMapper.selectList(testStepQueryWrapper);
                for (TestStep testStep : testSteps) {
                    if (StepType.ASSERT.equals(StepType.valueOf(testStep.getStepType()))) {
                        assertNumber++;
                    }
                    // 获取步骤关联的断言
                    Map<String, Object> stepDetail = new Gson().fromJson(testStep.getStepDetail().toString(),
                            new TypeToken<Map<String, Object>>() {
                            }.getType());
                    Object assertList = stepDetail.get("assertList");
                    if (assertList != null) {

                        assertNumber += ((List) assertList).size();
                    }
                }
            }
        }
        report.setSceneNumber(sceneNumber);
        report.setStepNumber(stepNumber);
        report.setAssertNumber(assertNumber);

        report.setPlanId(plan.getId());
        report.setPlanName(plan.getPlanName());
        report.setReportName(plan.getPlanName() + " - " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        report.setTaskType(plan.getTaskType().toString());
        report.setReportCategory(plan.getPlanCategory() == null || plan.getPlanCategory().isBlank()
                ? PlanCategory.UI.name() : plan.getPlanCategory());

        // 初始化将所有步骤、断言的状态都默认为是跳过
        report.setStepSkipNumber(stepNumber);
        report.setAssertSkipNumber(assertNumber);

        // 设置状态（执行中）
        report.setStatus(0);

        return report;
    }

    /**
     * 统一统计报告中的步骤/断言数量，并按报告类型区分统计口径
     */
    private void fillReportStats(Report report,
                                 java.util.Map<String, java.util.List<com.mokatest.platform.demos.result.StepResult>> allScenesResults,
                                 long taskStartTime) {
        int successStepNumber = 0;
        int errorStepNumber = 0;
        int skipStepNumber = 0;
        int assertSuccessNumber = 0;
        int assertErrorNumber = 0;
        int assertSkipNumber = 0;

        if (PlanCategory.API.name().equals(report.getReportCategory())) {
            // API 计划：递归统计所有步骤状态；断言从 BaseStepResult.assertResults 中统计
            for (java.util.List<com.mokatest.platform.demos.result.StepResult> stepResults : allScenesResults.values()) {
                for (com.mokatest.platform.demos.result.StepResult stepResult : stepResults) {
                    ApiPlanStats stats = collectApiStepStats(stepResult);
                    successStepNumber += stats.successSteps;
                    errorStepNumber += stats.errorSteps;
                    skipStepNumber += stats.skipSteps;
                    assertSuccessNumber += stats.successAsserts;
                    assertErrorNumber += stats.errorAsserts;
                    assertSkipNumber += stats.skipAsserts;
                }
            }
        } else {
            // UI 计划：保持原有统计逻辑（子步骤当作断言统计）
            for (java.util.List<com.mokatest.platform.demos.result.StepResult> stepResults : allScenesResults.values()) {
                for (com.mokatest.platform.demos.result.StepResult stepResult : stepResults) {
                    com.mokatest.platform.demos.result.BaseStepResult result = stepResult.getResult();
                    if (result != null) {
                        com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType status = result.getStatus();
                        if (status == com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType.SUCCESS) {
                            successStepNumber++;
                        } else if (status == com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType.FAILURE) {
                            errorStepNumber++;
                        } else if (status == com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType.SKIPPED) {
                            skipStepNumber++;
                        }
                    }
                    java.util.List<com.mokatest.platform.demos.result.StepResult> children = stepResult.getChildren();
                    if (children != null) {
                        for (com.mokatest.platform.demos.result.StepResult child : children) {
                            com.mokatest.platform.demos.result.BaseStepResult childResult = child.getResult();
                            if (childResult != null) {
                                com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType childStatus = childResult.getStatus();
                                if (childStatus == com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType.SUCCESS) {
                                    assertSuccessNumber++;
                                } else if (childStatus == com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType.FAILURE) {
                                    assertErrorNumber++;
                                } else if (childStatus == com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType.SKIPPED) {
                                    assertSkipNumber++;
                                }
                            }
                        }
                    }
                }
            }
        }

        report.setStepSuccessNumber(successStepNumber);
        report.setStepErrorNumber(errorStepNumber);
        report.setStepSkipNumber(skipStepNumber);
        report.setAssertSuccessNumber(assertSuccessNumber);
        report.setAssertErrorNumber(assertErrorNumber);
        report.setAssertSkipNumber(assertSkipNumber);
        report.setExecutionDuration((double) (System.currentTimeMillis() - taskStartTime));
        report.setEndTime(new Date());
        report.setStatus(1);
        reportMapper.updateById(report);
    }

    /**
     * 递归收集 API 计划步骤统计
     */
    private ApiPlanStats collectApiStepStats(com.mokatest.platform.demos.result.StepResult stepResult) {
        ApiPlanStats stats = new ApiPlanStats();
        com.mokatest.platform.demos.result.BaseStepResult result = stepResult.getResult();
        if (result != null) {
            com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType status = result.getStatus();
            if (status == com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType.SUCCESS) {
                stats.successSteps++;
            } else if (status == com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType.FAILURE) {
                stats.errorSteps++;
            } else if (status == com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType.SKIPPED) {
                stats.skipSteps++;
            }

            java.util.Map<Integer, com.mokatest.platform.demos.result.AssertResult> assertResults = result.getAssertResults();
            if (assertResults != null && !assertResults.isEmpty()) {
                for (com.mokatest.platform.demos.result.AssertResult ar : assertResults.values()) {
                    if (ar == null) continue;
                    if (Boolean.TRUE.equals(ar.getSuccess())) {
                        stats.successAsserts++;
                    } else {
                        stats.errorAsserts++;
                    }
                }
            }
        }

        java.util.List<com.mokatest.platform.demos.result.StepResult> children = stepResult.getChildren();
        if (children != null) {
            for (com.mokatest.platform.demos.result.StepResult child : children) {
                ApiPlanStats childStats = collectApiStepStats(child);
                stats.successSteps += childStats.successSteps;
                stats.errorSteps += childStats.errorSteps;
                stats.skipSteps += childStats.skipSteps;
                stats.successAsserts += childStats.successAsserts;
                stats.errorAsserts += childStats.errorAsserts;
                stats.skipAsserts += childStats.skipAsserts;
            }
        }
        return stats;
    }

    /**
     * API 计划统计内部类
     */
    private static class ApiPlanStats {
        int successSteps = 0;
        int errorSteps = 0;
        int skipSteps = 0;
        int successAsserts = 0;
        int errorAsserts = 0;
        int skipAsserts = 0;
    }

    /**
     * 获取任务状态。
     * 合并调度队列(taskMap)、立即执行(immediateRunningMap)、定时任务(scheduledRunningMap)三者的状态，
     * 返回每个计划ID对应的综合状态信息。
     */
    public Map<Integer, Object> getTaskStatus() {
        Map<Integer, Object> status = new HashMap<>();
        // 收集所有出现过的 planId（来自调度队列 + 立即执行 + 定时任务）
        Set<Integer> allTaskIds = new HashSet<>();
        allTaskIds.addAll(taskMap.keySet());
        allTaskIds.addAll(immediateRunningMap.keySet());
        allTaskIds.addAll(scheduledRunningMap.keySet());

        for (Integer taskId : allTaskIds) {
            Map<String, Object> taskStatus = new HashMap<>();
            ScheduledFuture<?> future = taskMap.get(taskId);
            taskStatus.put("scheduled", future != null && !future.isCancelled());
            // 分别返回两种执行类型的运行状态，便于前端/监控区分
            taskStatus.put("immediateRunning", immediateRunningMap.getOrDefault(taskId, false));
            taskStatus.put("scheduledRunning", scheduledRunningMap.getOrDefault(taskId, false));
            taskStatus.put("cancelled", future != null && future.isCancelled());

            status.put(taskId, taskStatus);
        }
        return status;
    }

    /**
     * 检查任务是否正在运行（任意类型：立即执行或定时任务）。
     *
     * @param taskName 计划ID
     * @return true 表示该计划有立即执行或定时任务正在运行
     */
    public boolean isTaskRunning(Integer taskName) {
        return immediateRunningMap.getOrDefault(taskName, false)
                || scheduledRunningMap.getOrDefault(taskName, false);
    }


    /**
     * 创建失败场景执行任务
     *
     * @param updateReportId
     * @param scenes
     */
    public Runnable createFailedScenarioTask(Integer updateReportId, List<Integer> scenes) {
        return () -> {
            // 记录 Runnable 真正开始执行的时刻，用于准确计算执行时长（不含 scheduler 排队时间）
            long taskStartTime = System.currentTimeMillis();

            Report report = reportMapper.selectById(updateReportId);
            // 获取关联的计划
            Plan plan = planMapper.selectById(report.getPlanId());
            if (plan == null) {
                throw new RuntimeException("计划不存在");
            }
            // 重试失败场景属于【立即执行】类型，使用 immediateRunningMap，
            // 与手动点击"立即执行"共享互斥逻辑，防止同一计划同时有两份手动执行在跑。
            immediateRunningMap.put(plan.getId(), true);
            // 状态设置为执行中
            report.setStatus(0);
            int insert = reportMapper.updateById(report);
            if (insert <= 0) {
                throw new RuntimeException("报告更新失败");
            }
            // 统一收集所有场景的结果，最后在 finally 中一次性写入报告
            java.util.Map<String, java.util.List<com.mokatest.platform.demos.result.StepResult>> allScenesResults = new java.util.concurrent.ConcurrentHashMap<>();
            try {
                SceneExecuteType sceneExecuteType = SceneExecuteType.valueOf(plan.getExecutionType().toString());
                String planCategory = plan.getPlanCategory() == null || plan.getPlanCategory().isBlank()
                        ? PlanCategory.UI.name() : plan.getPlanCategory();
                if (PlanCategory.API.name().equals(planCategory)) {
                    executeApiPlan(plan, scenes, report, sceneExecuteType, allScenesResults);
                } else {
                    String authContext = null;
                    // 执行前置的认证内容
                    if (plan.getSceneStatusExtract() != null) {
                        PlaywrightPlanSession playwrightPlanSession = new PlaywrightPlanSession();
                        playwrightPlanSession.setPlan(plan);
                        playwrightPlanSession.setReportId(report.getId());
                        Scene scene = sceneMapper.selectById(plan.getSceneStatusExtract());
                        if (scene != null) {
                            playwrightPlanSession.setAuthStep(stepBuilder.buildScenarioSteps(plan.getSceneStatusExtract()));
                            initSceneInfo(playwrightPlanSession, plan.getId(), plan.getSceneStatusExtract());
                        }
                        // 先执行认证场景
                        playwrightPlanSession.startAuthExecute();
                        // 获取认证内容
                        authContext = playwrightPlanSession.getAuthContext();
                    }
                    if (sceneExecuteType == SceneExecuteType.ORDER) {
                        // 将计划执行session对象提取出来，避免每次执行场景都初始化session对象，节省资源
                        // 目前是：同一个浏览器，多个上下文
                        PlaywrightPlanSession playwrightPlanSession = new PlaywrightPlanSession();
                        playwrightPlanSession.setPlan(plan);
                        playwrightPlanSession.setReportId(report.getId());
                        // 根据场景id处理构建场景
                        for (Integer sceneId : scenes) {
                        // 判断sceneId是否存在
                        Scene scene = sceneMapper.selectById(sceneId);
                        if (scene == null) {
                            // todo 考虑删除计划里边不存在的场景
                            continue;
                        }
                        playwrightPlanSession.setScenarioId(sceneId);
                        playwrightPlanSession.setSceneName(scene.getName());
                        if (authContext != null) {
                            playwrightPlanSession.setAuthContext(authContext);
                        }
                        List<StepTreeNode> stepTreeNodes = stepBuilder.buildScenarioSteps(sceneId);
                        // 每次需要清空步骤列表
                        playwrightPlanSession.getTestSteps().clear();
                        stepTreeNodes.forEach(playwrightPlanSession::addStep);
                        initSceneInfo(playwrightPlanSession, plan.getId(), sceneId);
                        // 等待上一个场景执行完之后在开始执行下一个
                        playwrightPlanSession.startPlanAndWait(plan.getId());
                        // 收集当前场景结果
                        java.util.List<com.mokatest.platform.demos.result.StepResult> sceneResults = playwrightPlanSession.getSceneStepResults();
                        if (sceneResults != null) {
                            allScenesResults.put(sceneId + "_" + scene.getName(), new java.util.ArrayList<>(sceneResults));
                        }
                    }
                    // 串行模式所有场景执行完后，关闭浏览器和Playwright资源
                    playwrightPlanSession.closeAllResources();
                } else if (sceneExecuteType == SceneExecuteType.PARALLEL) {
                    // 创建一个列表来存储所有的session，以便等待它们完成
                    List<PlaywrightPlanSession> sessions = new ArrayList<>();
                    // 复用全局场景执行线程池，避免每次执行计划都新建/销毁线程池
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(scenes.size());
                    for (Integer sceneId : scenes) {
                        // 判断sceneId是否存在
                        Scene scene = sceneMapper.selectById(sceneId);
                        if (scene == null) {
                            // todo 考虑删除计划里边不存在的场景
                            latch.countDown(); // 场景不存在也要减计数，防止死等
                            continue;
                        }

                        PlaywrightPlanSession playwrightPlanSession = new PlaywrightPlanSession();
                        playwrightPlanSession.setPlan(plan);
                        playwrightPlanSession.setReportId(report.getId());
                        playwrightPlanSession.setScenarioId(sceneId);
                        playwrightPlanSession.setSceneName(scene.getName());
                        // 停0.5s
                        Thread.sleep(500);
                        if (authContext != null) {
                            playwrightPlanSession.setAuthContext(authContext);
                        }

                        List<StepTreeNode> stepTreeNodes = stepBuilder.buildScenarioSteps(sceneId);
                        stepTreeNodes.forEach(playwrightPlanSession::addStep);
                        initSceneInfo(playwrightPlanSession, plan.getId(), sceneId);
                        sessions.add(playwrightPlanSession);

                        // 提交任务到全局线程池执行
                        planSceneExecutor.submit(() -> {
                            try {
                                playwrightPlanSession.executeTest();
                            } catch (Exception e) {
                                log.error("场景[{}]执行异常", scene.getName(), e);
                            } finally {
                                // 收集当前场景结果
                                java.util.List<com.mokatest.platform.demos.result.StepResult> sceneResults = playwrightPlanSession.getSceneStepResults();
                                if (sceneResults != null) {
                                    allScenesResults.put(sceneId + "_" + scene.getName(), new java.util.ArrayList<>(sceneResults));
                                }
                                latch.countDown(); // 任务完成，计数减1
                            }
                        });
                    }
                    // 等待所有任务完成
                    try {
                        boolean allDone = latch.await(60, TimeUnit.MINUTES);
                        if (!allDone) {
                            log.error("部分任务执行超时（60分钟），强制终止剩余任务");
                            // 先标记，让 worker 在步骤间隙主动退出，避免访问已关闭的资源
                            for (PlaywrightPlanSession session : sessions) {
                                session.setForceTerminated(true);
                            }
                            // 再关闭资源
                            for (PlaywrightPlanSession session : sessions) {
                                session.closeAllResources();
                            }
                        }
                    } catch (InterruptedException e) {
                        log.error("等待任务完成时被中断", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("❌ 任务执行失败: " + plan.getId() + " | 错误: " + e.getMessage());
            } finally {
                // 统一收集所有场景结果并写入报告
                Report report1 = reportMapper.selectById(report.getId());
                Gson gson = new Gson();
                report1.setScenes(gson.toJson(allScenesResults));

                // 统计步骤/断言数量并写入报告
                fillReportStats(report1, allScenesResults, taskStartTime);

                // 根据本次报告结果更新项目 UI 通过率（使用步骤通过率作为 UI 通过率近似值）
                try {
                    Integer reportProjectId = report1.getProjectId();
                    Integer stepNumber = report1.getStepNumber();
                    if (reportProjectId != null && stepNumber != null && stepNumber > 0) {
                        int stepSuccessNumber = report1.getStepSuccessNumber() == null ? 0 : report1.getStepSuccessNumber();
                        int uiPass = stepSuccessNumber * 100 / stepNumber;
                        Project project = new Project();
                        project.setId(reportProjectId);
                        project.setUiPass(uiPass);
                        projectMapper.updateById(project);
                    }
                } catch (Exception e) {
                    log.error("更新项目 UI 通过率失败（重试场景）, projectId={}, reportId={}", report1.getProjectId(), report1.getId(), e);
                }

                // ===== Webhook 通知发送（新增，不影响原有逻辑）=====
                // 发送失败仅记录日志，绝不抛异常影响主流程
                try {
                    if (webhookNotifyService != null) {
                        webhookNotifyService.send(plan, report1);
                    }
                } catch (Exception e) {
                    log.error("Webhook 通知发送异常（重试场景），planId={}", plan.getId(), e);
                }

                // 重试失败场景执行完毕，清理立即执行状态
                immediateRunningMap.put(plan.getId(), false);

            }
        };


    }


    /**
     * 重新执行失败的场景任务
     */
    public Boolean reExecuteFailedScenario(Integer updateReportId, List<Integer> scenes) {
        scheduler.submit(createFailedScenarioTask(updateReportId, scenes));
        return true;
    }

}