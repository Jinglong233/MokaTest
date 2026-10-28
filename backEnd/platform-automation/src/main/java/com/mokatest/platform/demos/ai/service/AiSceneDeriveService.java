package com.mokatest.platform.demos.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mokatest.platform.demos.ai.config.ModelManager;
import com.mokatest.platform.demos.ai.guardrail.PromptInjectionGuard;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.Scene;
import com.mokatest.platform.demos.domain.ui.SceneSetting;
import com.mokatest.platform.demos.domain.ui.Setting;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementType;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.listener.projectListener.ProjectUpdateEvent;
import com.mokatest.platform.demos.mapper.ElementMapper;
import com.mokatest.platform.demos.mapper.SceneMapper;
import com.mokatest.platform.demos.mapper.TestStepMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.mokatest.platform.demos.listener.projectListener.Enum.UpdateDataType.UI;

/**
 * AI 派生场景服务（批次二·步骤6）。
 *
 * 流程：请求线程 buildContext 解析并校验上下文（源场景摘要 + 勾选目录下元素摘要，硬上限防护）→
 * 异步线程 deriveStream 走 ModelManager/AssistantFactory 流式生成 →
 * 完成后解析 JSON、做幻觉元素校验（引用 id ∉ 勾选范围的步骤剔除并推 warning）→ 推 result。
 * 落库走 saveDerivedScene：复用场景新建链路（scene + test_step 同事务），
 * 标记 derive_from=源场景id，统一归入项目根下自动创建的「AI派生场景」目录。
 */
@Slf4j
@Service
public class AiSceneDeriveService {

    /** AI 允许输出且本平台可落库的步骤类型 */
    private static final Set<String> SUPPORTED_STEP_TYPES = Set.of(
            "OPEN_PAGE", "CLICK", "HOVER", "KEYBOARD", "WAIT", "ASSERT",
            "BACK", "FORWARD", "REFRESH", "CLOSE_PAGE");
    /** 必须引用库元素的步骤类型（缺 elementId 或引用越界即剔除） */
    private static final Set<String> ELEMENT_STEP_TYPES = Set.of("CLICK", "HOVER", "KEYBOARD", "ASSERT");
    private static final Set<String> CLICK_TYPES = Set.of("SINGLE_CLICK", "DOUBLE_CLICK", "RIGHT_CLICK");
    /** 覆盖方向编码 → 中文标签（与前端 aiSceneDerive.ts 的 DERIVE_COVERAGE 对齐） */
    private static final Map<String, String> COVERAGE_DIR_LABELS = Map.of(
            "NEGATIVE", "反用例", "BOUNDARY", "边界值", "EXCEPTION", "异常路径", "PERMISSION", "权限校验");

    @Resource
    private SceneMapper sceneMapper;
    @Resource
    private TestStepMapper testStepMapper;
    @Resource
    private ElementMapper elementMapper;
    @Resource
    private ModelManager modelManager;
    @Resource
    private AssistantFactory assistantFactory;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Gson gson = new Gson();

    // ==================== 上下文组装（请求线程内调用） ====================

    /**
     * 解析并校验派生上下文。所有校验失败直接抛 BusinessException（由控制器转为 SSE error 事件）。
     */
    public DeriveContext buildContext(DeriveRequestBody body) {
        Integer projectId = ProjectContextHolder.getProjectId();
        if (projectId == null) {
            throw new BusinessException("缺少项目上下文（X-Project-Id）");
        }
        if (body == null || body.getSceneId() == null) {
            throw new BusinessException("缺少源场景 sceneId");
        }
        String instruction = trimTo(body.getInstruction(), SceneDerivePrompts.MAX_INSTRUCTION_LENGTH, "派生指令");
        String bizRules = trimTo(body.getBizRules(), SceneDerivePrompts.MAX_BIZ_RULES_LENGTH, "业务规则");
        List<String> coverageDirs = sanitizeCoverageDirs(body.getCoverageDirs());
        // 提示注入前置拦截（模型侧另有 InputGuardrail 兜底）
        if (PromptInjectionGuard.containsInjection(instruction) || PromptInjectionGuard.containsInjection(bizRules)) {
            throw new BusinessException("检测到提示注入攻击，请求已被拦截");
        }
        for (String dir : coverageDirs) {
            if (PromptInjectionGuard.containsInjection(dir)) {
                throw new BusinessException("检测到提示注入攻击，请求已被拦截");
            }
        }

        // 源场景：归属校验 + 步骤摘要（不含完整 stepDetail 大 JSON）
        String pid = String.valueOf(projectId);
        Scene source = sceneMapper.selectById(body.getSceneId());
        if (source == null || !pid.equals(source.getProjectId())) {
            throw new BusinessException("源场景不存在或不属于当前项目");
        }
        if (!"SCENE".equals(String.valueOf(source.getSceneType()))) {
            throw new BusinessException("sceneId 指向的不是场景");
        }
        String sourceSummary = buildSourceSummary(source);

        // 勾选目录（含子目录）下的元素摘要
        List<Element> scopeElements = collectScopeElements(pid, body.getFolderIds());
        if (scopeElements.size() > SceneDerivePrompts.MAX_ELEMENTS) {
            throw new BusinessException("勾选目录下元素共 " + scopeElements.size() + " 个，超过上限 "
                    + SceneDerivePrompts.MAX_ELEMENTS + "，请缩小勾选范围");
        }

        DeriveContext ctx = new DeriveContext();
        ctx.setProjectId(projectId);
        ctx.setUserId(StpUtil.getLoginIdAsString());
        ctx.setSourceSceneId(source.getId());
        ctx.setSourceSummary(sourceSummary);
        ctx.setElementSummary(buildElementSummary(scopeElements));
        Set<Integer> allowed = new HashSet<>();
        for (Element e : scopeElements) {
            allowed.add(e.getId());
        }
        ctx.setAllowedElementIds(allowed);
        ctx.setInstruction(instruction);
        ctx.setBizRules(bizRules);
        ctx.setCoverageDirs(coverageDirs);
        return ctx;
    }

    private String buildSourceSummary(Scene source) {
        StringBuilder sb = new StringBuilder();
        sb.append("名称：").append(source.getName()).append('\n');
        if (StringUtils.hasText(source.getDescription())) {
            sb.append("描述：").append(abbreviate(source.getDescription(), 200)).append('\n');
        }
        QueryWrapper<TestStep> qw = new QueryWrapper<>();
        qw.eq("scenario_id", source.getId());
        qw.orderByAsc("order_index");
        List<TestStep> steps = testStepMapper.selectList(qw);
        sb.append("步骤摘要：");
        int limit = Math.min(steps.size(), SceneDerivePrompts.MAX_SOURCE_STEPS);
        for (int i = 0; i < limit; i++) {
            TestStep step = steps.get(i);
            sb.append('\n').append(i + 1).append(". ").append(step.getStepName())
                    .append("（").append(step.getStepType());
            String elementName = extractElementName(step.getStepDetail());
            if (elementName != null) {
                sb.append("，元素：").append(elementName);
            }
            String url = extractField(step.getStepDetail(), "url");
            if (url != null) {
                sb.append("，url：").append(abbreviate(url, 120));
            }
            sb.append("）");
        }
        if (steps.size() > limit) {
            sb.append("\n...（共 ").append(steps.size()).append(" 步，仅展示前 ").append(limit).append(" 步）");
        }
        return sb.toString();
    }

    /**
     * 收集勾选目录（含全部子孙目录）下的 ELEMENT 类型元素
     */
    private List<Element> collectScopeElements(String projectId, List<Integer> folderIds) {
        List<Element> result = new ArrayList<>();
        if (folderIds == null || folderIds.isEmpty()) {
            return result;
        }
        QueryWrapper<Element> qw = new QueryWrapper<>();
        qw.eq("project_id", projectId);
        List<Element> all = elementMapper.selectList(qw);
        Map<Integer, List<Element>> childrenMap = new HashMap<>();
        Map<Integer, Element> byId = new HashMap<>();
        for (Element e : all) {
            byId.put(e.getId(), e);
            childrenMap.computeIfAbsent(e.getParentId(), k -> new ArrayList<>()).add(e);
        }
        Set<Integer> visited = new HashSet<>();
        Deque<Integer> queue = new ArrayDeque<>();
        for (Integer folderId : folderIds) {
            if (folderId == null) {
                continue;
            }
            Element folder = byId.get(folderId);
            if (folder == null || !ElementType.FOLDER.name().equals(String.valueOf(folder.getElementType()))) {
                throw new BusinessException("勾选的元素目录 id=" + folderId + " 不存在或不是目录");
            }
            queue.add(folderId);
        }
        while (!queue.isEmpty()) {
            Integer current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            for (Element child : childrenMap.getOrDefault(current, List.of())) {
                if (ElementType.FOLDER.name().equals(String.valueOf(child.getElementType()))) {
                    queue.add(child.getId());
                } else {
                    result.add(child);
                }
            }
        }
        return result;
    }

    private String buildElementSummary(List<Element> elements) {
        StringBuilder sb = new StringBuilder();
        for (Element e : elements) {
            sb.append(e.getId()).append("｜").append(e.getElementName());
            if (StringUtils.hasText(e.getDescription())) {
                sb.append("｜").append(abbreviate(e.getDescription(), SceneDerivePrompts.MAX_ELEMENT_DESC));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ==================== 流式派生（异步线程内调用） ====================

    public void deriveStream(DeriveContext ctx, SseEmitter emitter) {
        String userMsg = SceneDerivePrompts.buildUserMessage(
                ctx.getSourceSummary(), ctx.getElementSummary(),
                ctx.getCoverageDirs().isEmpty() ? null : coverageDirsText(ctx.getCoverageDirs()),
                ctx.getBizRules(), ctx.getInstruction());
        StringBuilder full = new StringBuilder();
        CaseGenerationAssistant assistant = assistantFactory.createCaseGenerator(
                ctx.getProjectId(), SceneDerivePrompts.systemPrompt());
        TokenStream ts = assistant.generate(userMsg);
        ts.onPartialResponse(chunk -> {
                    if (chunk == null || chunk.isEmpty()) {
                        return;
                    }
                    full.append(chunk);
                    send(emitter, "delta", chunk);
                })
                .onCompleteResponse(response -> handleComplete(ctx, emitter, full.toString()))
                .onError(error -> {
                    send(emitter, "error", error.getMessage() != null ? error.getMessage() : "AI 派生失败");
                    emitter.complete();
                })
                .start();
    }

    private void handleComplete(DeriveContext ctx, SseEmitter emitter, String full) {
        try {
            JSONObject draft;
            try {
                draft = parseDraft(full);
            } catch (BusinessException parseError) {
                List<ChatMessage> retry = new ArrayList<>();
                retry.add(AiMessage.from(full));
                retry.add(UserMessage.from("上一次输出无法解析：" + parseError.getMessage()
                        + "，请严格按要求的 JSON 对象格式重新输出。"));
                draft = parseDraft(chatSync(retry));
            }
            List<String> warnings = new ArrayList<>();
            JSONObject result = validateAndClean(draft, ctx, warnings);
            for (String warning : warnings) {
                send(emitter, "warning", JSON.toJSONString(Map.of("message", warning)));
            }
            send(emitter, "result", result.toJSONString());
            emitter.complete();
        } catch (Exception e) {
            log.warn("AI 派生场景结果解析失败: {}", e.getMessage());
            send(emitter, "error", e instanceof BusinessException ? e.getMessage() : "生成结果解析失败，请重试");
            emitter.complete();
        }
    }

    private String coverageDirsText(List<String> coverageDirs) {
        List<String> labels = new ArrayList<>();
        for (String dir : coverageDirs) {
            labels.add(COVERAGE_DIR_LABELS.getOrDefault(dir, dir));
        }
        return String.join("、", labels);
    }

    private JSONObject parseDraft(String text) {
        if (text == null) {
            throw new BusinessException("模型未返回内容");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException("输出中未找到 JSON 对象");
        }
        try {
            return JSON.parseObject(text.substring(start, end + 1));
        } catch (Exception e) {
            throw new BusinessException("JSON 解析失败：" + e.getMessage());
        }
    }

    /**
     * 幻觉校验与清洗：步骤引用的元素 id 必须 ∈ 勾选范围；越界/缺引用的元素步骤剔除并收集 warning
     */
    private JSONObject validateAndClean(JSONObject draft, DeriveContext ctx, List<String> warnings) {
        String name = abbreviate(trimOrNull(draft.getString("name")), 100);
        if (name == null) {
            name = "AI派生场景";
            warnings.add("模型未输出场景名称，已使用默认名称");
        }
        String description = abbreviate(trimOrNull(draft.getString("description")), 500);

        JSONArray rawSteps = draft.getJSONArray("steps");
        if (rawSteps == null || rawSteps.isEmpty()) {
            throw new BusinessException("模型未生成任何步骤");
        }
        JSONArray cleaned = new JSONArray();
        int limit = Math.min(rawSteps.size(), SceneDerivePrompts.MAX_STEPS);
        if (rawSteps.size() > SceneDerivePrompts.MAX_STEPS) {
            warnings.add("步骤数超过上限 " + SceneDerivePrompts.MAX_STEPS + "，多余步骤已截断");
        }
        for (int i = 0; i < limit; i++) {
            Object item = rawSteps.get(i);
            if (!(item instanceof JSONObject)) {
                warnings.add("第 " + (i + 1) + " 个步骤格式非法，已剔除");
                continue;
            }
            JSONObject step = (JSONObject) item;
            String stepType = trimOrNull(step.getString("stepType"));
            stepType = stepType == null ? null : stepType.toUpperCase();
            if (stepType == null || !SUPPORTED_STEP_TYPES.contains(stepType)) {
                warnings.add("步骤「" + abbreviate(step.getString("stepName"), 30) + "」类型不支持（"
                        + stepType + "），已剔除");
                continue;
            }
            String stepName = abbreviate(trimOrNull(step.getString("stepName")), 100);
            if (stepName == null) {
                stepName = "步骤 " + (cleaned.size() + 1);
            }
            JSONObject detail = step.getJSONObject("stepDetail");
            if (detail == null) {
                detail = new JSONObject();
            }
            if (ELEMENT_STEP_TYPES.contains(stepType)) {
                Integer elementId = readElementId(detail);
                if (elementId == null) {
                    warnings.add("步骤「" + stepName + "」未引用元素，已剔除");
                    continue;
                }
                if (!ctx.getAllowedElementIds().contains(elementId)) {
                    warnings.add("步骤「" + stepName + "」引用了未勾选的元素 id=" + elementId + "（疑似幻觉），已剔除");
                    continue;
                }
            } else {
                detail.remove("elementId");
            }
            normalizeDetail(stepType, detail);
            JSONObject out = new JSONObject();
            out.put("stepType", stepType);
            out.put("stepName", stepName);
            out.put("stepDetail", detail);
            cleaned.add(out);
        }
        if (cleaned.isEmpty()) {
            throw new BusinessException("生成的步骤均因引用无效元素被剔除，请扩大元素勾选范围或调整派生指令后重试");
        }
        JSONObject result = new JSONObject();
        result.put("name", name);
        result.put("description", description);
        result.put("deriveFrom", ctx.getSourceSceneId());
        result.put("steps", cleaned);
        return result;
    }

    private void normalizeDetail(String stepType, JSONObject detail) {
        switch (stepType) {
            case "CLICK" -> {
                String clickType = trimOrNull(detail.getString("clickType"));
                detail.put("clickType", clickType != null && CLICK_TYPES.contains(clickType.toUpperCase())
                        ? clickType.toUpperCase() : "SINGLE_CLICK");
            }
            case "KEYBOARD" -> {
                detail.put("inputType", "NORMAL");
                if (detail.getString("inputValue") == null) {
                    detail.put("inputValue", "");
                }
            }
            case "WAIT" -> {
                detail.put("waitType", "TIME");
                Object waitTime = detail.get("waitTime");
                int seconds = waitTime instanceof Number ? ((Number) waitTime).intValue() : 2;
                detail.put("waitTime", Math.max(1, Math.min(seconds, 60)));
            }
            default -> {
            }
        }
    }

    private Integer readElementId(JSONObject detail) {
        Object value = detail.get("elementId");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Integer.valueOf(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    // ==================== 落库（复用场景新建链路） ====================

    /**
     * 派生确认后的落库：新建场景（derive_from=源场景id，归入「AI派生场景」目录）+ 批量插入步骤，
     * 步骤内 elementId 解析为库元素引用（locatorSource=LIBRARY）。
     */
    @Transactional
    public Map<String, Object> saveDerivedScene(DeriveSaveBody body) {
        Integer projectId = ProjectContextHolder.getProjectId();
        if (projectId == null) {
            throw new BusinessException("缺少项目上下文（X-Project-Id）");
        }
        if (body == null || body.getDeriveFrom() == null) {
            throw new BusinessException("缺少派生来源 deriveFrom（源场景 id）");
        }
        String name = trimOrNull(body.getName());
        if (name == null) {
            throw new BusinessException("缺少场景名称");
        }
        if (body.getSteps() == null || body.getSteps().isEmpty()) {
            throw new BusinessException("步骤列表不能为空");
        }
        String pid = String.valueOf(projectId);
        Scene source = sceneMapper.selectById(body.getDeriveFrom());
        if (source == null || !pid.equals(source.getProjectId())) {
            throw new BusinessException("源场景不存在或不属于当前项目");
        }
        String userId = StpUtil.getLoginIdAsString();

        Integer folderId = resolveDeriveFolderId(pid, userId);

        Scene scene = new Scene();
        scene.setProjectId(pid);
        scene.setParentId(folderId);
        scene.setName(abbreviate(name, 100));
        if (StringUtils.hasText(body.getDescription())) {
            scene.setDescription(abbreviate(body.getDescription().trim(), 500));
        }
        scene.setSceneType("SCENE");
        scene.setSceneCategory("UI");
        scene.setDeriveFrom(body.getDeriveFrom());
        QueryWrapper<Scene> countWrapper = new QueryWrapper<>();
        countWrapper.eq("parent_id", folderId);
        countWrapper.eq("project_id", pid);
        countWrapper.eq("scene_category", "UI");
        scene.setSort(sceneMapper.selectCount(countWrapper).intValue() + 1);
        scene.setSceneSetting(gson.toJson(new SceneSetting()));
        scene.setCreateUserId(userId);
        scene.setUpdateUserId(userId);
        sceneMapper.insert(scene);

        List<String> warnings = new ArrayList<>();
        int order = 1;
        int skipped = 0;
        for (DerivedStep draft : body.getSteps()) {
            TestStep step = buildStep(draft, pid, scene.getId(), order, userId, warnings);
            if (step == null) {
                skipped++;
                continue;
            }
            testStepMapper.insert(step);
            order++;
        }
        if (order == 1) {
            throw new BusinessException("所有步骤均因引用无效元素被跳过，未落库");
        }
        eventPublisher.publishEvent(new ProjectUpdateEvent(this, 1, pid, UI));

        Map<String, Object> result = new HashMap<>();
        result.put("sceneId", scene.getId());
        result.put("folderId", folderId);
        result.put("savedSteps", order - 1);
        result.put("skippedSteps", skipped);
        result.put("warnings", warnings);
        return result;
    }

    /**
     * 解析/创建项目根下的「AI派生场景」目录
     */
    private Integer resolveDeriveFolderId(String projectId, String userId) {
        QueryWrapper<Scene> qw = new QueryWrapper<>();
        qw.eq("project_id", projectId);
        qw.eq("parent_id", 0);
        qw.eq("scene_type", "FOLDER");
        qw.eq("scene_category", "UI");
        qw.eq("name", SceneDerivePrompts.DERIVE_FOLDER_NAME);
        qw.orderByAsc("sort").orderByAsc("id");
        qw.last("LIMIT 1");
        Scene folder = sceneMapper.selectOne(qw);
        if (folder != null) {
            return folder.getId();
        }
        folder = new Scene();
        folder.setProjectId(projectId);
        folder.setParentId(0);
        folder.setName(SceneDerivePrompts.DERIVE_FOLDER_NAME);
        folder.setSceneType("FOLDER");
        folder.setSceneCategory("UI");
        QueryWrapper<Scene> countWrapper = new QueryWrapper<>();
        countWrapper.eq("project_id", projectId);
        countWrapper.eq("parent_id", 0);
        countWrapper.eq("scene_category", "UI");
        folder.setSort(sceneMapper.selectCount(countWrapper).intValue() + 1);
        folder.setSceneSetting(gson.toJson(new SceneSetting()));
        folder.setCreateUserId(userId);
        folder.setUpdateUserId(userId);
        sceneMapper.insert(folder);
        log.info("[AiSceneDerive] 自动创建「{}」目录: projectId={}", SceneDerivePrompts.DERIVE_FOLDER_NAME, projectId);
        return folder.getId();
    }

    /**
     * 组装待落库步骤：elementId → ElementDTO 结构（locatorSource=LIBRARY，含定位链快照），
     * 元素不存在/跨项目时返回 null（跳过并记 warning）
     */
    @SuppressWarnings("unchecked")
    private TestStep buildStep(DerivedStep draft, String projectId, Integer sceneId, int order,
                               String userId, List<String> warnings) {
        if (draft == null || !StringUtils.hasText(draft.getStepType())) {
            warnings.add("存在缺 stepType 的步骤，已跳过");
            return null;
        }
        String stepType = draft.getStepType().trim().toUpperCase();
        if (!SUPPORTED_STEP_TYPES.contains(stepType)) {
            warnings.add("步骤「" + draft.getStepName() + "」类型不支持（" + stepType + "），已跳过");
            return null;
        }
        Map<String, Object> detail = draft.getStepDetail() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(draft.getStepDetail());
        if (ELEMENT_STEP_TYPES.contains(stepType)) {
            Integer elementId = null;
            Object raw = detail.remove("elementId");
            if (raw instanceof Number) {
                elementId = ((Number) raw).intValue();
            }
            Element element = elementId == null ? null : elementMapper.selectById(elementId);
            if (element == null || !projectId.equals(element.getProjectId())) {
                warnings.add("步骤「" + draft.getStepName() + "」引用的元素 id=" + elementId + " 不存在，已跳过该步骤");
                return null;
            }
            Map<String, Object> locator = new LinkedHashMap<>();
            locator.put("id", element.getId());
            locator.put("elementName", element.getElementName());
            locator.put("locatorType", element.getLocatorType());
            locator.put("locatorValue", element.getLocatorValue());
            locator.put("locatorChain", element.getLocatorChain());
            locator.put("projectId", projectId);
            Map<String, Object> elementDTO = new LinkedHashMap<>();
            elementDTO.put("locator", locator);
            elementDTO.put("customLocator", new LinkedHashMap<>());
            elementDTO.put("locatorSource", "LIBRARY");
            detail.put("element", elementDTO);
        } else {
            detail.remove("elementId");
        }
        String stepName = StringUtils.hasText(draft.getStepName()) ? draft.getStepName().trim() : "步骤 " + order;
        detail.put("stepType", stepType);
        detail.put("stepName", stepName);
        if (!(detail.get("setting") instanceof Map)) {
            detail.put("setting", objectMapper.convertValue(new Setting(), Map.class));
        }

        TestStep step = new TestStep();
        step.setScenarioId(sceneId.toString());
        step.setStepType(stepType);
        step.setStepName(stepName);
        step.setOrderIndex(order);
        step.setParentId(0);
        step.setProjectId(projectId);
        step.setIsDisable(0);
        step.setStepDetail(detail);
        step.setCreateUserId(userId);
        step.setUpdateUserId(userId);
        return step;
    }

    // ==================== 工具 ====================

    private String chatSync(List<ChatMessage> messages) {
        ChatRequest req = ChatRequest.builder().messages(messages).build();
        ChatResponse resp = modelManager.getChatModel().chat(req);
        return resp.aiMessage().text();
    }

    private String extractElementName(Object stepDetail) {
        Map<String, Object> detail = toMap(stepDetail);
        if (detail == null) {
            return null;
        }
        Object elementObj = detail.get("element");
        if (!(elementObj instanceof Map)) {
            return null;
        }
        Map<String, Object> elementMap = toMap(elementObj);
        for (String key : new String[]{"locator", "customLocator"}) {
            Map<String, Object> locator = elementMap == null ? null : toMap(elementMap.get(key));
            Object name = locator == null ? null : locator.get("elementName");
            if (name != null && StringUtils.hasText(name.toString())) {
                return abbreviate(name.toString(), 50);
            }
        }
        return null;
    }

    private String extractField(Object stepDetail, String field) {
        Map<String, Object> detail = toMap(stepDetail);
        Object value = detail == null ? null : detail.get(field);
        return value != null && StringUtils.hasText(value.toString()) ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        if (obj instanceof String && StringUtils.hasText((String) obj)) {
            try {
                return objectMapper.readValue((String) obj, Map.class);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private List<String> sanitizeCoverageDirs(List<String> coverageDirs) {
        List<String> result = new ArrayList<>();
        if (coverageDirs == null) {
            return result;
        }
        if (coverageDirs.size() > SceneDerivePrompts.MAX_COVERAGE_DIRS) {
            throw new BusinessException("覆盖方向最多 " + SceneDerivePrompts.MAX_COVERAGE_DIRS + " 个");
        }
        for (String dir : coverageDirs) {
            String trimmed = trimTo(dir, SceneDerivePrompts.MAX_COVERAGE_DIR_LENGTH, "覆盖方向");
            if (trimmed != null) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String trimTo(String text, int max, String label) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.length() > max) {
            throw new BusinessException(label + "长度超过上限 " + max + " 字符");
        }
        return trimmed;
    }

    private String trimOrNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException ignored) {
        }
    }

    // ==================== DTO ====================

    /** 派生请求体：{sceneId, instruction?, folderIds[], coverageDirs[], bizRules?} */
    @Data
    public static class DeriveRequestBody {
        private Integer sceneId;
        private String instruction;
        private List<Integer> folderIds;
        private List<String> coverageDirs;
        private String bizRules;
    }

    /** 落库请求体 */
    @Data
    public static class DeriveSaveBody {
        /** 派生来源：源场景 id */
        private Integer deriveFrom;
        private String name;
        private String description;
        private List<DerivedStep> steps;
    }

    /** 派生草稿步骤（result 事件与落库请求共用结构） */
    @Data
    public static class DerivedStep {
        private String stepType;
        private String stepName;
        private Map<String, Object> stepDetail;
    }

    /** 请求线程解析出的派生上下文（跨异步线程传递，不依赖 ThreadLocal） */
    @Data
    public static class DeriveContext {
        private Integer projectId;
        private String userId;
        private Integer sourceSceneId;
        private String sourceSummary;
        private String elementSummary;
        private Set<Integer> allowedElementIds;
        private String instruction;
        private String bizRules;
        private List<String> coverageDirs;
    }
}
