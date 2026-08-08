package com.mokatest.platform.demos.api.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.Environment;
import com.mokatest.platform.demos.api.domain.GlobalVar;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiType;
import com.mokatest.platform.demos.api.domain.apiEnum.RuleSource;
import com.mokatest.platform.demos.api.domain.requestModel.ApiSceneConfig;
import com.mokatest.platform.demos.api.domain.requestModel.AssertParameter;
import com.mokatest.platform.demos.api.domain.requestModel.RequestExecuteInfo;
import com.mokatest.platform.demos.api.domain.requestModel.RequestParameter;
import com.mokatest.platform.demos.api.domain.requestModel.SqlConfig;
import com.mokatest.platform.demos.api.domain.vo.ApiStepResponseVO;
import com.mokatest.platform.demos.api.http.assertion.ApiAssertExecutor;
import com.mokatest.platform.demos.api.http.validation.SchemaValidator;
import com.mokatest.platform.demos.api.http.executor.RequestExecutor;
import com.mokatest.platform.demos.api.http.executor.RequestExecutorFactory;
import com.mokatest.platform.demos.api.http.executor.impl.SqlRequestExecutor;
import com.mokatest.platform.demos.api.http.extraction.ExtractionDetail;
import com.mokatest.platform.demos.api.http.extraction.ExtractionExecutor;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import com.mokatest.platform.demos.api.mapper.EnvironmentMapper;
import com.mokatest.platform.demos.api.mapper.GlobalVarMapper;
import com.mokatest.platform.demos.domain.ui.Scene;
import com.mokatest.platform.demos.domain.ui.SceneSetting;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.AssertStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.ForStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.IFStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.ScriptStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.WaitStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.WhileStepDTO;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.condation.AssertRelationship;
import com.mokatest.platform.demos.domain.ui.uiEnum.condation.ConditionalRelationship;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.service.SceneService;
import com.mokatest.platform.demos.service.TestStepService;
import com.mokatest.platform.demos.api.script.ScriptContext;
import com.mokatest.platform.demos.api.script.ScriptExecutor;
import com.mokatest.platform.demos.step.stepImpl.ScriptStep;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.mokatest.platform.demos.util.VariableReplacer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * API场景调试服务
 *
 * 功能：纯API场景的执行引擎，按顺序执行场景下的所有步骤（支持层级结构）。
 * 支持步骤类型：
 *   - API_REQUEST: HTTP请求（含变量替换、提取、断言）
 *   - WAIT: 固定时长等待
 *   - IF: 条件判断（基于变量上下文），执行子步骤
 *   - FOR: 固定次数循环，执行子步骤
 *   - WHILE: 条件循环（基于变量上下文），执行子步骤
 *
 * 配置优先级：接口配置 > 场景配置 > 环境配置
 * 注意：本服务不依赖 Playwright 浏览器上下文，完全独立运行。
 */
@Slf4j
@Service
public class ApiSceneDebugService {

    @Autowired
    private TestStepService testStepService;

    @Autowired
    private ApiRequestMapper apiRequestMapper;

    @Autowired
    private RequestExecutorFactory requestExecutorFactory;

    @Autowired
    private SqlRequestExecutor sqlRequestExecutor;

    @Autowired
    private ExtractionExecutor extractionExecutor;

    @Autowired
    private ApiAssertExecutor apiAssertExecutor;

    @Autowired
    private GlobalVarMapper globalVarMapper;

    @Autowired
    private SceneService sceneService;

    @Autowired
    private EnvironmentMapper environmentMapper;

    /** 单场景调试最大允许执行时间（毫秒），防止死循环或大量请求导致线程长期占用 */
    private static final long MAX_SCENE_EXECUTION_TIME_MS = 5 * 60 * 1000L; // 5 分钟

    /** WebSocket 实时推送回调（ThreadLocal，每个调试线程独立） */
    private static final ThreadLocal<java.util.function.Consumer<ApiStepResult>> STEP_RESULT_CONSUMER = new ThreadLocal<>();

    /**
     * 调试API场景
     *
     * @param sceneId 场景ID
     * @return 场景执行结果
     */
    public ApiSceneDebugResult debugScene(Integer sceneId) {
        long sceneStartTime = System.currentTimeMillis();
        log.info("[API场景调试] 开始调试场景, sceneId={}", sceneId);

        // 1. 获取场景下的所有步骤
        List<TestStep> allSteps = testStepService.getStepBySceneId(sceneId);
        log.info("[API场景调试] 场景步骤数: {}", allSteps.size());

        if (allSteps.isEmpty()) {
            ApiSceneDebugResult emptyResult = new ApiSceneDebugResult();
            emptyResult.setStepResults(new ArrayList<>());
            emptyResult.setOverallSuccess(true);
            emptyResult.setMessage("场景下没有步骤");
            return emptyResult;
        }

        // 2. 获取场景配置
        ApiSceneConfig apiSceneConfig = resolveSceneConfig(sceneId);

        // 3. 获取全局变量（按团队ID）
        Integer teamId = resolveTeamId(allSteps);
        List<GlobalVar> globalVars = new ArrayList<>();
        if (teamId != null) {
            globalVars = globalVarMapper.selectList(
                    new QueryWrapper<GlobalVar>()
                            .eq("team_id", teamId)
                            .eq("disabled", false)
            );
        }

        // 4. 初始化步骤间共享的变量上下文
        // 优先级：全局变量 < 场景变量（同名覆盖）
        Map<String, Object> variables = new HashMap<>();
        for (GlobalVar gv : globalVars) {
            if (gv.getName() != null) {
                variables.put(gv.getName(), gv.getValue());
            }
        }
        if (apiSceneConfig != null && apiSceneConfig.getSceneVariables() != null) {
            for (RequestParameter rp : apiSceneConfig.getSceneVariables()) {
                if (rp.getName() != null && !rp.isDisabled()) {
                    variables.put(rp.getName(), rp.getValue());
                }
            }
        }

        // 5. 构建步骤树（parentId 分组）
        Map<Integer, List<TestStep>> stepTree = buildStepTree(allSteps);

        // 6. 获取顶级步骤（parentId 为 null 或 0）
        List<TestStep> topSteps = allSteps.stream()
                .filter(step -> step.getParentId() == null || step.getParentId() == 0)
                .sorted(Comparator.comparing(TestStep::getOrderIndex))
                .toList();

        // 7. 遍历执行顶级步骤（递归处理控制流）
        List<ApiStepResult> stepResults = new ArrayList<>();
        boolean stopOnFailure = true;
        long deadline = sceneStartTime + MAX_SCENE_EXECUTION_TIME_MS;

        for (TestStep step : topSteps) {
            if (step.getIsDisable() != null && step.getIsDisable() == 1) {
                log.info("[API场景调试] 跳过禁用步骤, stepId={}, stepName={}", step.getId(), step.getStepName());
                continue;
            }

            // 检查总执行时间是否超限
            if (System.currentTimeMillis() > deadline) {
                log.warn("[API场景调试] 场景执行时间超过最大限制({}ms)，强制终止", MAX_SCENE_EXECUTION_TIME_MS);
                ApiStepResult timeoutResult = createBaseResult(step);
                timeoutResult.setSuccess(false);
                timeoutResult.setStatus(StepExecutionType.FAILURE);
                timeoutResult.setErrorMessage("场景执行超时，已超过最大允许时间");
                timeoutResult.setEndTime(System.currentTimeMillis());
                stepResults.add(timeoutResult);
                break;
            }

            log.info("[API场景调试] 开始执行步骤, stepId={}, stepName={}, stepType={}", step.getId(), step.getStepName(), step.getStepType());
            ApiStepResult result = executeStep(step, stepTree, variables, apiSceneConfig);
            log.info("[API场景调试] 步骤执行结束, stepId={}, success={}, timeMs={}", step.getId(), result.isSuccess(), result.getTimeConsuming());
            stepResults.add(result);

            if (!result.isSuccess() && stopOnFailure) {
                log.info("[API场景调试] 步骤失败且开启失败停止, 中断后续步骤执行");
                break;
            }
        }

        long sceneTotalTime = System.currentTimeMillis() - sceneStartTime;
        boolean overallSuccess = stepResults.stream().allMatch(ApiStepResult::isSuccess);
        log.info("[API场景调试] 场景调试结束, sceneId={}, totalTimeMs={}, overallSuccess={}", sceneId, sceneTotalTime, overallSuccess);

        ApiSceneDebugResult finalResult = new ApiSceneDebugResult();
        finalResult.setStepResults(stepResults);
        finalResult.setOverallSuccess(overallSuccess);
        return finalResult;
    }

    /**
     * 实时调试API场景（WebSocket 流式推送）
     *
     * 与 debugScene() 执行逻辑完全一致，但每执行完一个顶级步骤就通过回调推送结果。
     * 循环/条件步骤：等所有子步骤执行完毕后推送父步骤结果（含完整 childrenResults）。
     *
     * @param sceneId         场景ID
     * @param onStepComplete  每完成一个步骤时的回调（由 WebSocket Handler 传入，负责推送）
     */
    public void debugSceneRealtime(Integer sceneId, java.util.function.Consumer<ApiStepResult> onStepComplete) {
        debugSceneRealtime(sceneId, null, null, onStepComplete);
    }

    /**
     * 实时调试，支持「从某步开始」/「仅执行某步」。
     *
     * @param fromStepId  从该顶级步骤开始执行（含），为 null 表示从头执行
     * @param onlyStepId  仅执行该顶级步骤，为 null 表示不限制；优先级高于 fromStepId
     */
    public void debugSceneRealtime(Integer sceneId, Integer fromStepId, Integer onlyStepId,
                                   java.util.function.Consumer<ApiStepResult> onStepComplete) {
        STEP_RESULT_CONSUMER.set(onStepComplete);
        try {
            doDebugSceneRealtime(sceneId, fromStepId, onlyStepId, onStepComplete);
        } finally {
            STEP_RESULT_CONSUMER.remove();
        }
    }

    private void doDebugSceneRealtime(Integer sceneId, Integer fromStepId, Integer onlyStepId,
                                      java.util.function.Consumer<ApiStepResult> onStepComplete) {
        long sceneStartTime = System.currentTimeMillis();
        log.info("[API场景调试-实时] 开始调试场景, sceneId={}", sceneId);

        // 1. 获取场景下的所有步骤
        List<TestStep> allSteps = testStepService.getStepBySceneId(sceneId);
        if (allSteps.isEmpty()) {
            log.info("[API场景调试-实时] 场景下没有步骤, sceneId={}", sceneId);
            return;
        }

        // 2. 获取场景配置
        ApiSceneConfig apiSceneConfig = resolveSceneConfig(sceneId);

        // 3. 获取全局变量（按团队ID）
        Integer teamId = resolveTeamId(allSteps);
        List<GlobalVar> globalVars = new ArrayList<>();
        if (teamId != null) {
            globalVars = globalVarMapper.selectList(
                    new QueryWrapper<GlobalVar>()
                            .eq("team_id", teamId)
                            .eq("disabled", false)
            );
        }

        // 4. 初始化步骤间共享的变量上下文
        Map<String, Object> variables = new HashMap<>();
        for (GlobalVar gv : globalVars) {
            if (gv.getName() != null) {
                variables.put(gv.getName(), gv.getValue());
            }
        }
        if (apiSceneConfig != null && apiSceneConfig.getSceneVariables() != null) {
            for (RequestParameter rp : apiSceneConfig.getSceneVariables()) {
                if (rp.getName() != null && !rp.isDisabled()) {
                    variables.put(rp.getName(), rp.getValue());
                }
            }
        }

        // 5. 构建步骤树
        Map<Integer, List<TestStep>> stepTree = buildStepTree(allSteps);

        // 6. 获取顶级步骤
        List<TestStep> topSteps = allSteps.stream()
                .filter(step -> step.getParentId() == null || step.getParentId() == 0)
                .sorted(Comparator.comparing(TestStep::getOrderIndex))
                .toList();

        // 6.1 单步 / 从某步开始：将传入的 stepId 归一为其顶级祖先，再裁剪 topSteps
        Integer onlyTopId = resolveTopAncestorId(onlyStepId, allSteps);
        Integer fromTopId = resolveTopAncestorId(fromStepId, allSteps);
        if (onlyTopId != null) {
            topSteps = topSteps.stream().filter(s -> onlyTopId.equals(s.getId())).toList();
            log.info("[API场景调试-实时] 仅执行单步, sceneId={}, stepId={}", sceneId, onlyTopId);
        } else if (fromTopId != null) {
            int fromIdx = -1;
            for (int i = 0; i < topSteps.size(); i++) {
                if (fromTopId.equals(topSteps.get(i).getId())) {
                    fromIdx = i;
                    break;
                }
            }
            if (fromIdx > 0) {
                topSteps = topSteps.subList(fromIdx, topSteps.size());
                log.info("[API场景调试-实时] 从指定步开始执行, sceneId={}, stepId={}, 跳过前{}个顶级步骤",
                        sceneId, fromTopId, fromIdx);
            }
        }

        // 7. 遍历执行顶级步骤，每完成一个立即推送
        boolean stopOnFailure = true;
        long deadline = sceneStartTime + MAX_SCENE_EXECUTION_TIME_MS;

        for (TestStep step : topSteps) {
            // 检查线程是否被中断（前端发送了终止命令）
            if (Thread.currentThread().isInterrupted()) {
                log.info("[API场景调试-实时] 线程被中断，终止后续执行, sceneId={}", sceneId);
                break;
            }

            if (step.getIsDisable() != null && step.getIsDisable() == 1) {
                continue;
            }

            // 检查总执行时间是否超限
            if (System.currentTimeMillis() > deadline) {
                log.warn("[API场景调试-实时] 场景执行时间超过最大限制({}ms)，强制终止", MAX_SCENE_EXECUTION_TIME_MS);
                ApiStepResult timeoutResult = createBaseResult(step);
                timeoutResult.setSuccess(false);
                timeoutResult.setStatus(StepExecutionType.FAILURE);
                timeoutResult.setErrorMessage("场景执行超时，已超过最大允许时间");
                timeoutResult.setEndTime(System.currentTimeMillis());
                onStepComplete.accept(timeoutResult);
                break;
            }

            log.info("[API场景调试-实时] 开始执行步骤, stepId={}, stepName={}, stepType={}",
                    step.getId(), step.getStepName(), step.getStepType());
            ApiStepResult result = executeStep(step, stepTree, variables, apiSceneConfig);
            log.info("[API场景调试-实时] 步骤执行结束, stepId={}, success={}, timeMs={}",
                    step.getId(), result.isSuccess(), result.getTimeConsuming());

            // 推送结果到 WebSocket
            onStepComplete.accept(result);

            if (!result.isSuccess() && stopOnFailure) {
                break;
            }
        }

        long sceneTotalTime = System.currentTimeMillis() - sceneStartTime;
        log.info("[API场景调试-实时] 场景调试结束, sceneId={}, totalTimeMs={}", sceneId, sceneTotalTime);
    }

    /**
     * 推送父步骤的当前执行进度（用于 WebSocket 实时展示）
     * 在控制流步骤（FOR/WHILE/IF）每执行完一个子步骤后调用，
     * 让前端能看到 childrenResults 逐步增长的效果。
     */
    private void notifyStepProgress(TestStep step, List<ApiStepResult> currentChildren) {
        java.util.function.Consumer<ApiStepResult> consumer = STEP_RESULT_CONSUMER.get();
        if (consumer == null) return;

        ApiStepResult snapshot = createBaseResult(step);
        boolean allSuccess = currentChildren.stream().allMatch(ApiStepResult::isSuccess);
        snapshot.setSuccess(allSuccess);
        snapshot.setStatus(allSuccess ? StepExecutionType.SUCCESS : StepExecutionType.FAILURE);
        snapshot.setErrorMessage("执行中，已完成 " + currentChildren.size() + " 个子步骤");
        snapshot.setEndTime(System.currentTimeMillis());

        // 轻量级的 childrenResults：不包含 response body，减少序列化和网络传输开销
        List<ApiStepResult> liteChildren = currentChildren.stream().map(child -> {
            ApiStepResult lite = new ApiStepResult();
            lite.setStepId(child.getStepId());
            lite.setStepName(child.getStepName());
            lite.setSuccess(child.isSuccess());
            lite.setStatus(child.getStatus());
            lite.setErrorMessage(child.getErrorMessage());
            lite.setStartTime(child.getStartTime());
            lite.setEndTime(child.getEndTime());
            lite.setAssertionResults(child.getAssertionResults());
            lite.setExtractedVariables(child.getExtractedVariables());
            // 中间推送不携带 response，最终推送时才携带
            lite.setResponse(null);
            lite.setChildrenResults(child.getChildrenResults());
            return lite;
        }).collect(Collectors.toList());
        snapshot.setChildrenResults(liteChildren);

        consumer.accept(snapshot);
    }

    /**
     * 将任意步骤ID归一为它所在的顶级步骤ID（沿 parentId 向上回溯）。
     * 传入 null 或找不到时返回 null。用于「单步/从此处执行」时把嵌套子步骤映射到其顶级步骤。
     */
    private Integer resolveTopAncestorId(Integer stepId, List<TestStep> allSteps) {
        if (stepId == null) return null;
        Map<Integer, TestStep> byId = new HashMap<>();
        for (TestStep s : allSteps) {
            byId.put(s.getId(), s);
        }
        TestStep cur = byId.get(stepId);
        if (cur == null) return null;
        int guard = 0;
        while (cur.getParentId() != null && cur.getParentId() != 0 && guard++ < 100) {
            TestStep parent = byId.get(cur.getParentId());
            if (parent == null) break;
            cur = parent;
        }
        return cur.getId();
    }

    /**
     * 构建步骤树：按 parentId 分组
     */
    private Map<Integer, List<TestStep>> buildStepTree(List<TestStep> allSteps) {
        Map<Integer, List<TestStep>> tree = new HashMap<>();
        for (TestStep step : allSteps) {
            Integer parentId = step.getParentId() != null ? step.getParentId() : 0;
            tree.computeIfAbsent(parentId, k -> new ArrayList<>()).add(step);
        }
        // 每组内按 orderIndex 排序
        tree.values().forEach(list -> list.sort(Comparator.comparing(TestStep::getOrderIndex)));
        return tree;
    }

    /**
     * 获取指定步骤的子步骤
     */
    private List<TestStep> getChildren(TestStep step, Map<Integer, List<TestStep>> stepTree) {
        return stepTree.getOrDefault(step.getId(), new ArrayList<>());
    }

    /**
     * 递归执行单个步骤（支持控制流）
     */
    private ApiStepResult executeStep(TestStep step, Map<Integer, List<TestStep>> stepTree,
                                       Map<String, Object> variables, ApiSceneConfig apiSceneConfig) {
        String stepType = step.getStepType();
        try {
            switch (stepType) {
                case "API_REQUEST":
                    return executeApiRequestStep(step, variables, apiSceneConfig);
                case "SQL":
                    return executeSqlStep(step, variables, apiSceneConfig);
                case "WAIT":
                    return executeWaitStep(step);
                case "SCRIPT":
                    return executeScriptStep(step, variables);
                case "IF":
                    return executeIfStep(step, stepTree, variables, apiSceneConfig);
                case "FOR":
                    return executeForStep(step, stepTree, variables, apiSceneConfig);
                case "WHILE":
                    return executeWhileStep(step, stepTree, variables, apiSceneConfig);
                default:
                    return createSkipResult(step, "不支持的步骤类型: " + stepType);
            }
        } catch (Exception e) {
            log.error("[API场景调试] 步骤执行异常, stepId={}, stepName={}, stepType={}", step.getId(), step.getStepName(), stepType, e);
            ApiStepResult result = createBaseResult(step);
            result.setSuccess(false);
            result.setStatus(StepExecutionType.FAILURE);
            result.setErrorMessage("步骤执行异常: " + e.getMessage());
            result.setEndTime(System.currentTimeMillis());
            return result;
        }
    }

    // ==================== WAIT 步骤 ====================

    private ApiStepResult executeWaitStep(TestStep step) {
        ApiStepResult result = createBaseResult(step);
        try {
            WaitStepDTO waitDto = parseStepDetail(step, WaitStepDTO.class);
            if (waitDto != null && waitDto.getWaitTime() != null && waitDto.getWaitTime() > 0) {
                Thread.sleep(waitDto.getWaitTime() * 1000L);
            }
            result.setSuccess(true);
            result.setStatus(StepExecutionType.SUCCESS);
            result.setErrorMessage("等待 " + (waitDto != null ? waitDto.getWaitTime() : 0) + " 秒");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.setSuccess(false);
            result.setStatus(StepExecutionType.FAILURE);
            result.setErrorMessage("等待被中断");
        } catch (Exception e) {
            result.setSuccess(false);
            result.setStatus(StepExecutionType.FAILURE);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setEndTime(System.currentTimeMillis());
        }
        return result;
    }

    // ==================== SCRIPT 步骤 ====================

    private static Integer parseProjectId(String projectId) {
        try {
            return projectId != null ? Integer.valueOf(projectId) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ApiStepResult executeScriptStep(TestStep step, Map<String, Object> variables) {
        ApiStepResult result = createBaseResult(step);
        try {
            ScriptStepDTO scriptDto = parseStepDetail(step, ScriptStepDTO.class);
            String content = scriptDto != null ? scriptDto.getScriptContent() : null;
            if (content == null || content.trim().isEmpty()) {
                result.setSuccess(true);
                result.setStatus(StepExecutionType.SUCCESS);
                result.setErrorMessage("脚本内容为空，跳过执行");
                return result;
            }

            // 执行脚本并回写场景变量池（ScriptStep.executeScript 内部合并）
            ScriptExecutor.ScriptResult scriptResult = ScriptStep.executeScript(content, variables,
                    parseProjectId(step.getProjectId()));

            // 脚本自定义断言 → 断言结果列表，任一失败则步骤失败
            boolean assertionFailed = false;
            if (scriptResult.getScriptAssertions() != null && !scriptResult.getScriptAssertions().isEmpty()) {
                List<AssertResult> assertList = new ArrayList<>();
                for (ScriptContext.ScriptAssertion sa : scriptResult.getScriptAssertions()) {
                    assertList.add(new AssertResult(sa.isSuccess(), sa.getMessage(), "SCRIPT"));
                    if (!sa.isSuccess()) {
                        assertionFailed = true;
                    }
                }
                result.setAssertionResults(assertList);
            }

            // 日志沿用 errorMessage 字段展示（与 WAIT 步骤的提示信息同一通路）
            String logs = scriptResult.getConsoleLogs() != null && !scriptResult.getConsoleLogs().isEmpty()
                    ? String.join("\n", scriptResult.getConsoleLogs())
                    : null;

            if (!scriptResult.isSuccess()) {
                result.setSuccess(false);
                result.setStatus(StepExecutionType.FAILURE);
                result.setErrorMessage("脚本执行失败: " + scriptResult.getErrorMessage()
                        + (logs != null ? "\n" + logs : ""));
            } else if (assertionFailed) {
                result.setSuccess(false);
                result.setStatus(StepExecutionType.FAILURE);
                result.setErrorMessage("存在脚本断言失败" + (logs != null ? "\n" + logs : ""));
            } else {
                result.setSuccess(true);
                result.setStatus(StepExecutionType.SUCCESS);
                result.setErrorMessage(logs != null ? logs : "脚本执行成功");
            }
        } catch (Exception e) {
            result.setSuccess(false);
            result.setStatus(StepExecutionType.FAILURE);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setEndTime(System.currentTimeMillis());
        }
        return result;
    }

    // ==================== IF 步骤 ====================

    private ApiStepResult executeIfStep(TestStep step, Map<Integer, List<TestStep>> stepTree,
                                         Map<String, Object> variables, ApiSceneConfig apiSceneConfig) {
        ApiStepResult result = createBaseResult(step);
        List<ApiStepResult> childResults = new ArrayList<>();

        try {
            IFStepDTO ifDto = parseStepDetail(step, IFStepDTO.class);
            boolean conditionMet = evaluateConditions(ifDto, variables);

            if (conditionMet) {
                List<TestStep> children = getChildren(step, stepTree);
                for (TestStep child : children) {
                    if (child.getIsDisable() != null && child.getIsDisable() == 1) continue;
                    ApiStepResult childResult = executeStep(child, stepTree, variables, apiSceneConfig);
                    childResults.add(childResult);
                    notifyStepProgress(step, childResults);
                    if (!childResult.isSuccess()) break;
                }
                result.setSuccess(childResults.stream().allMatch(ApiStepResult::isSuccess));
                result.setErrorMessage("条件满足，执行子步骤");
            } else {
                result.setSuccess(true);
                result.setErrorMessage("条件不满足，跳过子步骤");
            }
            result.setStatus(result.isSuccess() ? StepExecutionType.SUCCESS : StepExecutionType.FAILURE);
            result.setChildrenResults(childResults);

        } catch (Exception e) {
            result.setSuccess(false);
            result.setStatus(StepExecutionType.FAILURE);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setEndTime(System.currentTimeMillis());
        }
        return result;
    }

    // ==================== FOR 步骤 ====================

    private ApiStepResult executeForStep(TestStep step, Map<Integer, List<TestStep>> stepTree,
                                          Map<String, Object> variables, ApiSceneConfig apiSceneConfig) {
        ApiStepResult result = createBaseResult(step);
        List<ApiStepResult> loopResults = new ArrayList<>();

        try {
            ForStepDTO forDto = parseStepDetail(step, ForStepDTO.class);
            int times = forDto != null && forDto.getCycleTimes() != null ? forDto.getCycleTimes() : 1;
            if (times < 1) times = 1;
            if (times > 9999) times = 9999;

            log.info("[API场景调试] FOR循环开始, stepId={}, 计划循环{}次, 子步骤数={}", step.getId(), times, getChildren(step, stepTree).size());

            List<TestStep> children = getChildren(step, stepTree);

            for (int i = 0; i < times; i++) {
                for (TestStep child : children) {
                    if (child.getIsDisable() != null && child.getIsDisable() == 1) continue;
                    ApiStepResult childResult = executeStep(child, stepTree, variables, apiSceneConfig);
                    loopResults.add(childResult);
                    notifyStepProgress(step, loopResults);
                    if (!childResult.isSuccess()) break;
                }
                if (loopResults.stream().anyMatch(r -> !r.isSuccess())) break;
            }

            result.setSuccess(loopResults.stream().allMatch(ApiStepResult::isSuccess));
            result.setStatus(result.isSuccess() ? StepExecutionType.SUCCESS : StepExecutionType.FAILURE);
            result.setErrorMessage("循环 " + times + " 次");
            result.setChildrenResults(loopResults);
            log.info("[API场景调试] FOR循环结束, stepId={}, 实际执行{}次子步骤", step.getId(), loopResults.size());

        } catch (Exception e) {
            log.error("[API场景调试] FOR循环异常, stepId={}", step.getId(), e);
            result.setSuccess(false);
            result.setStatus(StepExecutionType.FAILURE);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setEndTime(System.currentTimeMillis());
        }
        return result;
    }

    // ==================== WHILE 步骤 ====================

    private ApiStepResult executeWhileStep(TestStep step, Map<Integer, List<TestStep>> stepTree,
                                            Map<String, Object> variables, ApiSceneConfig apiSceneConfig) {
        ApiStepResult result = createBaseResult(step);
        List<ApiStepResult> loopResults = new ArrayList<>();

        try {
            WhileStepDTO whileDto = parseStepDetail(step, WhileStepDTO.class);
            int maxLoop = whileDto != null && whileDto.getMaxLoopCount() != null ? whileDto.getMaxLoopCount() : 1;
            if (maxLoop < 1) maxLoop = 1;
            if (maxLoop > 9999) maxLoop = 9999;

            log.info("[API场景调试] WHILE循环开始, stepId={}, maxLoop={}, 子步骤数={}", step.getId(), maxLoop, getChildren(step, stepTree).size());

            List<TestStep> children = getChildren(step, stepTree);
            int loopCount = 0;

            while (loopCount < maxLoop) {
                boolean conditionMet = evaluateConditions(whileDto, variables);
                if (!conditionMet) break;

                for (TestStep child : children) {
                    if (child.getIsDisable() != null && child.getIsDisable() == 1) continue;
                    ApiStepResult childResult = executeStep(child, stepTree, variables, apiSceneConfig);
                    loopResults.add(childResult);
                    notifyStepProgress(step, loopResults);
                    if (!childResult.isSuccess()) break;
                }
                loopCount++;
                if (loopResults.stream().anyMatch(r -> !r.isSuccess())) break;
            }

            result.setSuccess(loopResults.stream().allMatch(ApiStepResult::isSuccess));
            result.setStatus(result.isSuccess() ? StepExecutionType.SUCCESS : StepExecutionType.FAILURE);
            result.setErrorMessage("循环 " + loopCount + " 次（最大 " + maxLoop + "）");
            result.setChildrenResults(loopResults);
            log.info("[API场景调试] WHILE循环结束, stepId={}, 实际循环{}次, 子步骤执行{}次", step.getId(), loopCount, loopResults.size());

        } catch (Exception e) {
            log.error("[API场景调试] WHILE循环异常, stepId={}", step.getId(), e);
            result.setSuccess(false);
            result.setStatus(StepExecutionType.FAILURE);
            result.setErrorMessage(e.getMessage());
        } finally {
            result.setEndTime(System.currentTimeMillis());
        }
        return result;
    }

    // ==================== 条件评估 ====================

    /**
     * 评估 IF/WHILE 条件
     * 在 API 场景中，条件基于变量上下文评估：
     *   - assertText 视为变量名，从变量上下文中获取值
     *   - exceptValue 为预期值
     *   - assertRelationship 为比较关系
     */
    private boolean evaluateConditions(Object dto, Map<String, Object> variables) {
        List<AssertStepDTO> conditionList = null;
        ConditionalRelationship relationship = ConditionalRelationship.AND;

        if (dto instanceof IFStepDTO) {
            conditionList = ((IFStepDTO) dto).getConditionList();
            relationship = ((IFStepDTO) dto).getConditionalRelationship();
        } else if (dto instanceof WhileStepDTO) {
            conditionList = ((WhileStepDTO) dto).getConditionList();
            relationship = ((WhileStepDTO) dto).getConditionalRelationship();
        }

        if (conditionList == null || conditionList.isEmpty()) {
            return true; // 无条件视为通过
        }

        List<Boolean> results = new ArrayList<>();
        for (AssertStepDTO condition : conditionList) {
            if (condition == null) continue;
            boolean singleResult = evaluateSingleCondition(condition, variables);
            results.add(singleResult);
        }

        if (results.isEmpty()) return true;

        if (relationship == ConditionalRelationship.OR) {
            return results.stream().anyMatch(Boolean::booleanValue);
        } else {
            return results.stream().allMatch(Boolean::booleanValue);
        }
    }

    private boolean evaluateSingleCondition(AssertStepDTO condition, Map<String, Object> variables) {
        String varName = condition.getAssertText(); // 变量名
        String expected = condition.getExceptValue(); // 预期值
        AssertRelationship rel = condition.getAssertRelationship();

        if (varName == null || varName.trim().isEmpty()) {
            return false;
        }

        Object actualObj = variables.get(varName.trim());
        String actual = actualObj != null ? actualObj.toString() : "";
        expected = expected != null ? expected : "";

        if (rel == null) rel = AssertRelationship.EQUALS;

        return switch (rel) {
            case EQUALS -> actual.equals(expected);
            case NOT_EQUALS -> !actual.equals(expected);
            case CONTAINS -> actual.contains(expected);
            case NOT_CONTAINS -> !actual.contains(expected);
            case GT -> compareNumeric(actual, expected) > 0;
            case LT -> compareNumeric(actual, expected) < 0;
            case GE -> compareNumeric(actual, expected) >= 0;
            case LE -> compareNumeric(actual, expected) <= 0;
            case REGULAR -> Pattern.matches(expected, actual);
        };
    }

    private int compareNumeric(String a, String b) {
        try {
            double da = Double.parseDouble(a);
            double db = Double.parseDouble(b);
            return Double.compare(da, db);
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    // ==================== API 请求步骤（原有逻辑）====================

    private ApiStepResult executeApiRequestStep(TestStep step, Map<String, Object> variables,
                                                 ApiSceneConfig apiSceneConfig) {
        ApiStepResult result = createBaseResult(step);
        List<AssertResult> allAssertionResults = new ArrayList<>();
        Map<String, Object> extractedVariables = new HashMap<>();
        ApiRequest apiRequest = null;

        try {
            Object stepDetail = step.getStepDetail();
            if (stepDetail == null) {
                result.setSuccess(false);
                result.setErrorMessage("步骤详情为空");
                result.setStatus(StepExecutionType.FAILURE);
                return result;
            }

            Map<String, Object> detailMap = JSON.parseObject(stepDetail.toString(), Map.class);

            Object apiConfigObj = detailMap.get("apiConfig");
            if (apiConfigObj != null) {
                String apiConfigJson = apiConfigObj instanceof String
                        ? (String) apiConfigObj
                        : JSON.toJSONString(apiConfigObj);
                apiRequest = JSON.parseObject(apiConfigJson, ApiRequest.class);
            }

            if (apiRequest == null) {
                Integer apiRequestId = detailMap.get("apiRequestId") != null
                        ? Integer.valueOf(detailMap.get("apiRequestId").toString())
                        : null;

                if (apiRequestId == null) {
                    result.setSuccess(false);
                    result.setErrorMessage("未配置API请求");
                    result.setStatus(StepExecutionType.FAILURE);
                    return result;
                }

                apiRequest = apiRequestMapper.selectById(apiRequestId);
                if (apiRequest == null) {
                    result.setSuccess(false);
                    result.setErrorMessage("API接口不存在: " + apiRequestId);
                    result.setStatus(StepExecutionType.FAILURE);
                    return result;
                }
            }

            log.debug("[API场景调试] 执行HTTP请求, stepId={}, apiName={}, method={}, path={}",
                    step.getId(), apiRequest.getApiName(), apiRequest.getRequestMethod(), apiRequest.getRequestPath());

            mergeSceneConfig(apiRequest, apiSceneConfig);
            mergeEnvironmentConfig(apiRequest, apiSceneConfig);

            // 场景没有环境配置：运行时强制忽略步骤自己的 baseUrl 和服务地址
            // 注意：场景模式下手动选择的服务地址不会设置 envId，因此不能通过 envId 判断，直接强制清空
            if (apiSceneConfig != null && apiSceneConfig.getEnvironmentId() == null
                    && apiRequest.getEnvInfo() != null) {
                apiRequest.getEnvInfo().setBaseUrl(null);
                apiRequest.getEnvInfo().setServe(null);
                apiRequest.getEnvInfo().setEnvId(null);
                apiRequest.getEnvInfo().setEnvName(null);
            }

            injectStepVariables(apiRequest, variables);

            RequestExecutor executor = requestExecutorFactory.getExecutor(apiRequest.getRequestMethod());
            if (executor == null) {
                result.setSuccess(false);
                result.setErrorMessage("不支持的请求方法: " + apiRequest.getRequestMethod());
                result.setStatus(StepExecutionType.FAILURE);
                return result;
            }

            TestHttpResponse response = executor.execute(apiRequest);
            log.debug("[API场景调试] HTTP请求返回, stepId={}, statusCode={}, success={}",
                    step.getId(), response.getStatusCode(), response.isSuccess());

            List<ExtractionDetail> extractionDetails = new ArrayList<>();
            if (apiRequest.getAssociationExtraction() != null && !apiRequest.getAssociationExtraction().isEmpty()) {
                extractedVariables = extractionExecutor.execute(apiRequest.getAssociationExtraction(), response);
                if (extractedVariables != null && !extractedVariables.isEmpty()) {
                    variables.putAll(extractedVariables);
                    log.debug("[API场景调试] 关联提取完成, stepId={}, extractedVars={}", step.getId(), extractedVariables.keySet());
                }
                // 提取详情（接口自身的提取规则来源为 API）
                extractionDetails = extractionExecutor.executeWithDetails(apiRequest.getAssociationExtraction(), response, RuleSource.API);
            }

            response.setExtractedVariables(extractedVariables);
            response.setExtractionDetails(extractionDetails);

            if (apiRequest.getApiResultAssert() != null && !apiRequest.getApiResultAssert().isEmpty()) {
                for (AssertParameter ap : apiRequest.getApiResultAssert()) {
                    if (ap != null) ap.setSource(RuleSource.API);
                }
                List<AssertResult> interfaceAsserts = apiAssertExecutor.execute(apiRequest.getApiResultAssert(), response);
                allAssertionResults.addAll(interfaceAsserts);
            }
            if (apiSceneConfig != null && apiSceneConfig.getSceneAssertions() != null
                    && !apiSceneConfig.getSceneAssertions().isEmpty()) {
                for (AssertParameter ap : apiSceneConfig.getSceneAssertions()) {
                    if (ap != null) ap.setSource(RuleSource.SCENE);
                }
                List<AssertResult> sceneAsserts = apiAssertExecutor.execute(apiSceneConfig.getSceneAssertions(), response);
                allAssertionResults.addAll(sceneAsserts);
            }
            // 响应结构校验（响应定义开启时自动执行）
            AssertResult schemaResult = SchemaValidator.validate(apiRequest.getResponseSchema(), response);
            if (schemaResult != null) {
                allAssertionResults.add(schemaResult);
            }

            response.setAssertionResults(allAssertionResults);

            ApiStepResponseVO responseVO = convertToResponseVO(response);
            result.setResponse(responseVO);
            // 使用副本避免与 response 中的同名字段共享引用，防止 FastJSON 序列化时输出 $ref
            result.setAssertionResults(new ArrayList<>(allAssertionResults));
            result.setExtractedVariables(new HashMap<>(extractedVariables));

            boolean allAssertionsPassed = allAssertionResults.stream()
                    .allMatch(r -> Boolean.TRUE.equals(r.getSuccess()));
            boolean stepSuccess = response.isSuccess() && allAssertionsPassed;
            result.setSuccess(stepSuccess);
            result.setStatus(stepSuccess ? StepExecutionType.SUCCESS : StepExecutionType.FAILURE);

            if (!response.isSuccess()) {
                result.setErrorMessage("HTTP状态码: " + response.getStatusCode());
            } else if (!allAssertionsPassed) {
                long failCount = allAssertionResults.stream()
                        .filter(r -> !Boolean.TRUE.equals(r.getSuccess())).count();
                result.setErrorMessage("断言失败: " + failCount + "/" + allAssertionResults.size());
            }

        } catch (Exception e) {
            log.error("[API场景调试] HTTP请求执行异常, stepId={}", step.getId(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setStatus(StepExecutionType.FAILURE);

            // 构建包含请求信息的错误响应，供前端排查问题
            if (apiRequest != null) {
                ApiStepResponseVO errorResponse = buildErrorResponseVO(apiRequest, e);
                result.setResponse(errorResponse);
            }
        } finally {
            result.setEndTime(System.currentTimeMillis());
        }
        return result;
    }

    // ==================== SQL 步骤 ====================

    /**
     * 执行 SQL 步骤
     * 支持内联 SqlConfig 或引用 DB 中的 SQL 类型 ApiRequest
     */
    private ApiStepResult executeSqlStep(TestStep step, Map<String, Object> variables,
                                          ApiSceneConfig apiSceneConfig) {
        ApiStepResult result = createBaseResult(step);
        List<AssertResult> allAssertionResults = new ArrayList<>();
        Map<String, Object> extractedVariables = new HashMap<>();
        ApiRequest apiRequest = null;

        try {
            Object stepDetail = step.getStepDetail();
            if (stepDetail == null) {
                result.setSuccess(false);
                result.setErrorMessage("SQL 步骤详情为空");
                result.setStatus(StepExecutionType.FAILURE);
                return result;
            }

            Map<String, Object> detailMap = JSON.parseObject(stepDetail.toString(), Map.class);

            // 解析 SQL 接口配置：优先 apiConfig 完整副本（与 API_REQUEST 步骤一致的场景副本模式），
            // 其次内联 sqlConfig，最后通过 apiRequestId 引用
            Object apiConfigObj = detailMap.get("apiConfig");
            if (apiConfigObj != null) {
                String apiConfigJson = apiConfigObj instanceof String
                        ? (String) apiConfigObj
                        : JSON.toJSONString(apiConfigObj);
                apiRequest = JSON.parseObject(apiConfigJson, ApiRequest.class);
            }

            Object sqlConfigObj = detailMap.get("sqlConfig");
            if (apiRequest == null && sqlConfigObj != null) {
                // 内联 SQL 配置：构建临时 ApiRequest
                String sqlConfigJson = sqlConfigObj instanceof String
                        ? (String) sqlConfigObj
                        : JSON.toJSONString(sqlConfigObj);
                SqlConfig sqlConfig = JSON.parseObject(sqlConfigJson, SqlConfig.class);

                apiRequest = new ApiRequest();
                apiRequest.setApiType(ApiType.SQL);
                apiRequest.setSqlConfig(sqlConfig);
                apiRequest.setApiName(step.getStepName());
                apiRequest.setProjectId(step.getProjectId() != null ? Integer.valueOf(step.getProjectId()) : null);
            }

            if (apiRequest == null) {
                Integer apiRequestId = detailMap.get("apiRequestId") != null
                        ? Integer.valueOf(detailMap.get("apiRequestId").toString())
                        : null;

                if (apiRequestId != null) {
                    apiRequest = apiRequestMapper.selectById(apiRequestId);
                    if (apiRequest == null) {
                        result.setSuccess(false);
                        result.setErrorMessage("SQL 接口不存在: " + apiRequestId);
                        result.setStatus(StepExecutionType.FAILURE);
                        return result;
                    }
                }
            }

            if (apiRequest == null) {
                result.setSuccess(false);
                result.setErrorMessage("未配置 SQL 语句或接口引用");
                result.setStatus(StepExecutionType.FAILURE);
                return result;
            }

            // 注入场景环境 ID（用于解析数据库连接）
            if (apiSceneConfig != null && apiSceneConfig.getEnvironmentId() != null
                    && apiRequest.getEnvInfo() == null) {
                RequestExecuteInfo envInfo = new RequestExecuteInfo();
                envInfo.setEnvId(apiSceneConfig.getEnvironmentId());
                apiRequest.setEnvInfo(envInfo);
            }

            // 注入步骤变量
            injectStepVariables(apiRequest, variables);

            log.debug("[API场景调试] 执行SQL步骤, stepId={}, dbName={}",
                    step.getId(), apiRequest.getSqlConfig() != null ? apiRequest.getSqlConfig().getDbConnectionName() : "N/A");

            // 执行 SQL
            TestHttpResponse response = sqlRequestExecutor.execute(apiRequest);
            log.debug("[API场景调试] SQL步骤返回, stepId={}, status={}, rows={}",
                    step.getId(), response.getStatus(),
                    response.getResponseHeaders() != null ? response.getResponseHeaders().get("X-Sql-Row-Count") : "N/A");

            // SQL 提取/断言已在 SqlRequestExecutor 内部按 sqlConfig.sqlExtractions/sqlAssertions 执行完毕，
            // 结果挂在 response 上：这里回写场景变量池并汇总断言结果
            if (response.getExtractedVariables() != null && !response.getExtractedVariables().isEmpty()) {
                extractedVariables.putAll(response.getExtractedVariables());
            }
            if (response.getAssertionResults() != null && !response.getAssertionResults().isEmpty()) {
                allAssertionResults.addAll(response.getAssertionResults());
            }

            // 场景级断言
            if (apiSceneConfig != null && apiSceneConfig.getSceneAssertions() != null && !apiSceneConfig.getSceneAssertions().isEmpty()) {
                for (AssertParameter assertion : apiSceneConfig.getSceneAssertions()) {
                    // 复制后注入变量替换
                    AssertParameter resolvedAssertion = new AssertParameter();
                    BeanUtils.copyProperties(assertion, resolvedAssertion);
                    resolvedAssertion.setField(VariableReplacer.replace(resolvedAssertion.getField(), variables));
                    resolvedAssertion.setAssertValue(VariableReplacer.replace(resolvedAssertion.getAssertValue(), variables));
                    List<AssertResult> sceneAssertResults = apiAssertExecutor.execute(
                            java.util.Collections.singletonList(resolvedAssertion), response);
                    allAssertionResults.addAll(sceneAssertResults);
                }
            }

            // 接口自身断言
            List<AssertParameter> apiAssertions = apiRequest.getApiResultAssert();
            if (apiAssertions != null && !apiAssertions.isEmpty()) {
                List<AssertResult> apiAssertResults = apiAssertExecutor.execute(apiAssertions, response);
                allAssertionResults.addAll(apiAssertResults);
            }
            result.setAssertionResults(allAssertionResults);

            boolean allAssertionsPassed = allAssertionResults.stream()
                    .allMatch(r -> Boolean.TRUE.equals(r.getSuccess()));
            boolean stepSuccess = response.isSuccess() && allAssertionsPassed;
            result.setSuccess(stepSuccess);
            result.setStatus(stepSuccess ? StepExecutionType.SUCCESS : StepExecutionType.FAILURE);
            result.setExtractedVariables(extractedVariables);

            if (!response.isSuccess()) {
                result.setErrorMessage(response.getErrorMessage() != null && !response.getErrorMessage().isEmpty()
                        ? response.getErrorMessage() : "SQL 执行失败");
            } else if (!allAssertionsPassed) {
                long failCount = allAssertionResults.stream()
                        .filter(r -> !Boolean.TRUE.equals(r.getSuccess())).count();
                result.setErrorMessage("断言失败: " + failCount + "/" + allAssertionResults.size());
            }

            // 将提取变量合并到共享变量上下文
            variables.putAll(extractedVariables);

            // 构建响应 VO
            ApiStepResponseVO vo = new ApiStepResponseVO();
            vo.setStatus(response.getStatus());
            vo.setApiId(response.getApiId());
            vo.setApiName(response.getApiName());
            vo.setRequestMethod("SQL");
            vo.setRequestUrl(response.getRequestUrl());
            vo.setRequestHeaders(response.getRequestHeaders());
            vo.setResponseHeaders(response.getResponseHeaders());
            vo.setErrorMessage(response.getErrorMessage());
            vo.setBodyAsString(new String(response.getRawBody() != null ? response.getRawBody() : new byte[0], java.nio.charset.StandardCharsets.UTF_8));
            vo.setStatusCode(response.getStatusCode());
            vo.setResponseTimeMs(response.getResponseTimeMs());
            vo.setExtractedVariables(new HashMap<>(extractedVariables));
            result.setResponse(vo);
        } catch (Exception e) {
            log.error("[API场景调试] SQL步骤执行异常, stepId={}, stepName={}", step.getId(), step.getStepName(), e);
            result.setSuccess(false);
            result.setStatus(StepExecutionType.FAILURE);
            result.setErrorMessage("SQL 步骤执行异常: " + e.getMessage());
        } finally {
            result.setEndTime(System.currentTimeMillis());
        }
        return result;
    }

    /**
     * 构建请求失败时的响应 VO，包含请求详情供前端展示
     * 
     * 注意：异常发生时变量替换尚未完成（替换在 AbstractRequestExecutor 内部执行），
     * 因此需要在这里手动进行变量替换，确保前端看到的是替换后的请求信息。
     * 变量不存在的保持原样不替换。
     */
    private ApiStepResponseVO buildErrorResponseVO(ApiRequest apiRequest, Exception e) {
        ApiStepResponseVO vo = new ApiStepResponseVO();
        vo.setStatus("error");
        vo.setApiId(apiRequest.getId());
        vo.setApiName(apiRequest.getApiName());
        vo.setRequestMethod(apiRequest.getRequestMethod() != null ? apiRequest.getRequestMethod().name() : "UNKNOWN");

        // 构建变量上下文（与 AbstractRequestExecutor 逻辑一致）
        Map<String, Object> variables = new HashMap<>();
        if (apiRequest.getEnvInfo() != null && apiRequest.getEnvInfo().getEnvVariables() != null) {
            variables.putAll(apiRequest.getEnvInfo().getEnvVariables());
        }

        // 变量替换辅助方法：对文本进行变量替换，变量不存在保持原样
        java.util.function.Function<String, String> replaceVars = text -> {
            if (text == null || text.isEmpty() || variables.isEmpty()) {
                return text;
            }
            return VariableReplacer.replace(text, variables);
        };

        // 构建请求 URL（使用替换后的 requestPath）
        String rawRequestPath = apiRequest.getRequestPath();
        String requestPath = replaceVars.apply(rawRequestPath);
        String baseUrl = apiRequest.getEnvInfo() != null ? apiRequest.getEnvInfo().getBaseUrl() : null;
        // baseUrl 也可能包含变量
        String resolvedBaseUrl = replaceVars.apply(baseUrl);
        String fullUrl;
        if (requestPath != null && (requestPath.startsWith("http://") || requestPath.startsWith("https://"))) {
            fullUrl = requestPath;
        } else if (resolvedBaseUrl != null && !resolvedBaseUrl.isEmpty() && requestPath != null) {
            boolean baseEndsWithSlash = resolvedBaseUrl.endsWith("/");
            boolean pathStartsWithSlash = requestPath.startsWith("/");
            if (baseEndsWithSlash && pathStartsWithSlash) {
                fullUrl = resolvedBaseUrl + requestPath.substring(1);
            } else if (!baseEndsWithSlash && !pathStartsWithSlash) {
                fullUrl = resolvedBaseUrl + "/" + requestPath;
            } else {
                fullUrl = resolvedBaseUrl + requestPath;
            }
        } else {
            fullUrl = requestPath != null ? requestPath : resolvedBaseUrl != null ? resolvedBaseUrl : "";
        }
        vo.setRequestUrl(fullUrl);

        // 请求头（值进行变量替换）
        if (apiRequest.getRequestHeader() != null) {
            Map<String, String> headers = new HashMap<>();
            for (var h : apiRequest.getRequestHeader()) {
                if (h != null && !h.isDisabled() && h.getName() != null) {
                    headers.put(h.getName(), replaceVars.apply(h.getValue()));
                }
            }
            vo.setRequestHeaders(headers);
        }

        // 请求体（内容进行变量替换）
        if (apiRequest.getBody() != null) {
            com.mokatest.platform.demos.api.domain.requestModel.Body body = apiRequest.getBody();
            if (body.getJson() != null) {
                vo.setRequestBody(replaceVars.apply(body.getJson()));
            } else if (body.getXml() != null) {
                vo.setRequestBody(replaceVars.apply(body.getXml()));
            } else if (body.getFormData() != null && !body.getFormData().isEmpty()) {
                // formData 是对象列表，需要对每个对象的 value 字段替换
                List<RequestParameter> replacedFormData = new ArrayList<>();
                for (var p : body.getFormData()) {
                    if (p != null) {
                        RequestParameter copy = new RequestParameter();
                        copy.setName(p.getName());
                        copy.setValue(replaceVars.apply(p.getValue()));
                        copy.setType(p.getType());
                        copy.setDescription(p.getDescription());
                        copy.setDisabled(p.isDisabled());
                        replacedFormData.add(copy);
                    }
                }
                vo.setRequestBody(JSON.toJSONString(replacedFormData));
            } else if (body.getXWwwFormUrlencoded() != null && !body.getXWwwFormUrlencoded().isEmpty()) {
                List<RequestParameter> replacedForm = new ArrayList<>();
                for (var p : body.getXWwwFormUrlencoded()) {
                    if (p != null) {
                        RequestParameter copy = new RequestParameter();
                        copy.setName(p.getName());
                        copy.setValue(replaceVars.apply(p.getValue()));
                        copy.setType(p.getType());
                        copy.setDescription(p.getDescription());
                        copy.setDisabled(p.isDisabled());
                        replacedForm.add(copy);
                    }
                }
                vo.setRequestBody(JSON.toJSONString(replacedForm));
            }
        }

        // Cookie（值进行变量替换）
        if (apiRequest.getCookies() != null && !apiRequest.getCookies().isEmpty()) {
            Map<String, String> cookies = new HashMap<>();
            for (var c : apiRequest.getCookies()) {
                if (c != null && !c.isDisabled() && c.getName() != null) {
                    cookies.put(c.getName(), replaceVars.apply(c.getValue()));
                }
            }
            vo.setCookies(cookies);
        }

        // Query 参数（值进行变量替换）
        if (apiRequest.getQuery() != null && !apiRequest.getQuery().isEmpty()) {
            Map<String, String> queryMap = new HashMap<>();
            for (var q : apiRequest.getQuery()) {
                if (q != null && !q.isDisabled() && q.getName() != null) {
                    queryMap.put(q.getName(), replaceVars.apply(q.getValue()));
                }
            }
            if (!queryMap.isEmpty()) {
                vo.setRequestUrl(vo.getRequestUrl() + "?" + queryMap.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("&")));
            }
        }

        vo.setErrorMessage(e.getMessage());
        vo.setResponseTimeMs(0);
        vo.setStatusCode(0);
        return vo;
    }

    // ==================== 工具方法 ====================

    private <T> T parseStepDetail(TestStep step, Class<T> clazz) {
        Object stepDetail = step.getStepDetail();
        if (stepDetail == null) return null;
        String json = stepDetail instanceof String ? (String) stepDetail : JSON.toJSONString(stepDetail);
        return JSON.parseObject(json, clazz);
    }

    private ApiStepResult createBaseResult(TestStep step) {
        ApiStepResult result = new ApiStepResult();
        result.setStepId(step.getId());
        result.setStepName(step.getStepName());
        result.setStartTime(System.currentTimeMillis());
        return result;
    }

    private ApiStepResult createSkipResult(TestStep step, String message) {
        ApiStepResult result = createBaseResult(step);
        result.setSuccess(true);
        result.setStatus(StepExecutionType.SKIPPED);
        result.setErrorMessage(message);
        result.setEndTime(System.currentTimeMillis());
        return result;
    }

    /**
     * 解析场景的API场景配置
     */
    private ApiSceneConfig resolveSceneConfig(Integer sceneId) {
        Scene scene = sceneService.getById(sceneId);
        if (scene == null || scene.getSceneSetting() == null) {
            return null;
        }
        try {
            SceneSetting sceneSetting = JSON.parseObject(scene.getSceneSetting(), SceneSetting.class);
            return sceneSetting != null ? sceneSetting.getApiSceneConfig() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从步骤列表中解析团队ID
     */
    private Integer resolveTeamId(List<TestStep> allSteps) {
        for (TestStep step : allSteps) {
            if (!"API_REQUEST".equals(step.getStepType())) continue;
            Object stepDetail = step.getStepDetail();
            if (stepDetail == null) continue;
            try {
                Map<String, Object> detailMap = JSON.parseObject(stepDetail.toString(), Map.class);
                Object apiConfigObj = detailMap.get("apiConfig");
                if (apiConfigObj != null) {
                    String apiConfigJson = apiConfigObj instanceof String
                            ? (String) apiConfigObj
                            : JSON.toJSONString(apiConfigObj);
                    ApiRequest req = JSON.parseObject(apiConfigJson, ApiRequest.class);
                    if (req != null && req.getTeamId() != null) {
                        return req.getTeamId();
                    }
                }
                Object apiRequestIdObj = detailMap.get("apiRequestId");
                if (apiRequestIdObj != null) {
                    Integer apiRequestId = Integer.valueOf(apiRequestIdObj.toString());
                    ApiRequest req = apiRequestMapper.selectById(apiRequestId);
                    if (req != null && req.getTeamId() != null) {
                        return req.getTeamId();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void mergeSceneConfig(ApiRequest apiRequest, ApiSceneConfig apiSceneConfig) {
        if (apiSceneConfig == null) return;
        if (apiRequest.getEnvInfo() == null) {
            apiRequest.setEnvInfo(new RequestExecuteInfo());
        }
        RequestExecuteInfo envInfo = apiRequest.getEnvInfo();

        if (apiSceneConfig.getSceneHeaders() != null && !apiSceneConfig.getSceneHeaders().isEmpty()) {
            if (envInfo.getEnvHeaders() == null) {
                envInfo.setEnvHeaders(new ArrayList<>());
            }
            for (RequestParameter header : apiSceneConfig.getSceneHeaders()) {
                if (header != null && !header.isDisabled()) {
                    envInfo.getEnvHeaders().add(header);
                }
            }
        }

        if (apiSceneConfig.getSceneCookies() != null && !apiSceneConfig.getSceneCookies().isEmpty()) {
            if (envInfo.getEnvCookies() == null) {
                envInfo.setEnvCookies(new ArrayList<>());
            }
            for (RequestParameter cookie : apiSceneConfig.getSceneCookies()) {
                if (cookie != null && !cookie.isDisabled()) {
                    envInfo.getEnvCookies().add(cookie);
                }
            }
        }
    }

    /**
     * 将环境配置合并到 API 请求中
     *
     * 合并策略（优先级从低到高）：
     *   环境配置 < 场景配置（已在 mergeSceneConfig 中处理）< 接口配置
     *
     * 合并内容：
     *   - baseUrl：如果接口没有设置 baseUrl，使用环境 serve 中的第一个服务地址
     *   - 变量：环境变量先 put，后续场景变量和接口变量覆盖
     *   - Header：环境 Header 追加到列表前面（场景 Header 和接口 Header 后追加，优先级更高）
     *   - Cookie：环境 Cookie 追加到列表前面
     *
     * @param apiRequest    API 请求配置
     * @param apiSceneConfig 场景配置（包含 environmentId）
     */
    private void mergeEnvironmentConfig(ApiRequest apiRequest, ApiSceneConfig apiSceneConfig) {
        if (apiSceneConfig == null || apiSceneConfig.getEnvironmentId() == null) {
            return;
        }

        Environment env = environmentMapper.selectById(apiSceneConfig.getEnvironmentId());
        if (env == null) {
            log.warn("[API场景调试] 环境配置不存在, environmentId={}", apiSceneConfig.getEnvironmentId());
            return;
        }

        if (apiRequest.getEnvInfo() == null) {
            apiRequest.setEnvInfo(new RequestExecuteInfo());
        }
        RequestExecuteInfo envInfo = apiRequest.getEnvInfo();

        // 1. 合并 baseUrl：接口未设置时使用环境的第一个 serve 地址
        if ((envInfo.getBaseUrl() == null || envInfo.getBaseUrl().isEmpty())
                && env.getServe() != null && !env.getServe().isEmpty()) {
            // 使用第一个启用的服务地址
            for (var serve : env.getServe()) {
                if (serve != null && serve.getAddress() != null && !serve.getAddress().isEmpty()) {
                    envInfo.setBaseUrl(serve.getAddress());
                    break;
                }
            }
        }

        // 2. 合并环境变量（优先级最低，先 put，后续场景变量和接口变量可覆盖）
        if (env.getEnvVar() != null && !env.getEnvVar().isEmpty()) {
            Map<String, String> mergedVars = new HashMap<>();
            for (RequestParameter varParam : env.getEnvVar()) {
                if (varParam != null && !varParam.isDisabled() && varParam.getName() != null) {
                    mergedVars.put(varParam.getName(), varParam.getValue());
                }
            }
            // 将已有变量（如全局变量）合并进去
            if (envInfo.getEnvVariables() != null) {
                mergedVars.putAll(envInfo.getEnvVariables());
            }
            envInfo.setEnvVariables(mergedVars);
        }

        // 3. 合并环境 Header（优先级最低，在列表前面）
        if (env.getHeaders() != null && !env.getHeaders().isEmpty()) {
            List<RequestParameter> mergedHeaders = new ArrayList<>();
            for (RequestParameter header : env.getHeaders()) {
                if (header != null && !header.isDisabled()) {
                    mergedHeaders.add(header);
                }
            }
            // 将已有的 Header（如场景 Header）追加到后面（优先级更高）
            if (envInfo.getEnvHeaders() != null) {
                mergedHeaders.addAll(envInfo.getEnvHeaders());
            }
            envInfo.setEnvHeaders(mergedHeaders);
        }

        // 4. 合并环境 Cookie（优先级最低，在列表前面）
        if (env.getCookies() != null && !env.getCookies().isEmpty()) {
            List<RequestParameter> mergedCookies = new ArrayList<>();
            for (RequestParameter cookie : env.getCookies()) {
                if (cookie != null && !cookie.isDisabled()) {
                    mergedCookies.add(cookie);
                }
            }
            // 将已有的 Cookie（如场景 Cookie）追加到后面（优先级更高）
            if (envInfo.getEnvCookies() != null) {
                mergedCookies.addAll(envInfo.getEnvCookies());
            }
            envInfo.setEnvCookies(mergedCookies);
        }

        log.debug("[API场景调试] 环境配置合并完成, environmentId={}, envName={}, baseUrl={}",
                env.getId(), env.getEnvName(), envInfo.getBaseUrl());
    }

    private void injectStepVariables(ApiRequest apiRequest, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) return;
        if (apiRequest.getEnvInfo() == null) {
            apiRequest.setEnvInfo(new RequestExecuteInfo());
        }
        Map<String, String> envVars = apiRequest.getEnvInfo().getEnvVariables();
        if (envVars == null) {
            envVars = new HashMap<>();
            apiRequest.getEnvInfo().setEnvVariables(envVars);
        }
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            envVars.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
        }
    }

    private ApiStepResponseVO convertToResponseVO(TestHttpResponse response) {
        if (response == null) return null;
        ApiStepResponseVO vo = new ApiStepResponseVO();
        vo.setApiId(response.getApiId());
        vo.setApiName(response.getApiName());
        vo.setStatus(response.getStatus());
        vo.setRequestUrl(response.getRequestUrl());
        vo.setRequestMethod(response.getRequestMethod());
        vo.setRequestHeaders(response.getRequestHeaders());
        if (response.getRequestBody() != null) {
            vo.setRequestBody(new String(response.getRequestBody(), java.nio.charset.StandardCharsets.UTF_8));
        }
        vo.setStatusCode(response.getStatusCode());
        vo.setResponseStatusMsg(response.getResponseStatusMsg());
        vo.setResponseHeaders(response.getResponseHeaders());
        vo.setCookies(response.getCookies());
        vo.setBodyAsString(response.getBodyAsString());
        vo.setResponseBytes(response.getResponseBytes());
        vo.setResponseTimeMs(response.getResponseTimeMs());
        vo.setRequestBytes(response.getRequestBytes());
        // 深拷贝断言结果，避免与 ApiStepResult.assertionResults 共享引用导致 FastJSON $ref
        if (response.getAssertionResults() != null) {
            List<AssertResult> copied = new ArrayList<>();
            for (AssertResult ar : response.getAssertionResults()) {
                AssertResult copy = new AssertResult();
                BeanUtils.copyProperties(ar, copy);
                copied.add(copy);
            }
            vo.setAssertionResults(copied);
        } else {
            vo.setAssertionResults(null);
        }
        vo.setExtractedVariables(response.getExtractedVariables());
        vo.setExtractionDetails(response.getExtractionDetails());
        vo.setVariableTrack(response.getVariableTrack());
        vo.setScriptConsoleLog(response.getScriptConsoleLog());
        vo.setScriptAssertions(response.getScriptAssertions());
        vo.setErrorMessage(response.getErrorMessage());
        return vo;
    }

    // ==================== 结果类 ====================

    @Data
    public static class ApiSceneDebugResult {
        private List<ApiStepResult> stepResults;
        private boolean overallSuccess;
        private String message;
    }

    @Data
    public static class ApiStepResult {
        private Integer stepId;
        private String stepName;
        private boolean success;
        private StepExecutionType status;
        private String errorMessage;
        private ApiStepResponseVO response;
        private long startTime;
        private long endTime;
        private List<AssertResult> assertionResults;
        private Map<String, Object> extractedVariables;

        /**
         * 子步骤执行结果（IF/FOR/WHILE 使用）
         */
        private List<ApiStepResult> childrenResults;

        public long getTimeConsuming() {
            return endTime - startTime;
        }
    }
}
