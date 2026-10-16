package com.mokatest.platform.demos.qa.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.config.ModelManager;
import com.mokatest.platform.demos.ai.context.ContextBlocks;
import com.mokatest.platform.demos.ai.domain.AiGenerationRecord;
import com.mokatest.platform.demos.ai.domain.Citation;
import com.mokatest.platform.demos.ai.service.AiGenerationRecordService;
import com.mokatest.platform.demos.ai.service.AgentAssistant;
import com.mokatest.platform.demos.ai.service.AssistantFactory;
import com.mokatest.platform.demos.ai.service.CasePointQaAssistant;
import com.mokatest.platform.demos.ai.service.CaseGenerationAssistant;
import com.mokatest.platform.demos.ai.service.CaseQaAssistant;
import com.mokatest.platform.demos.ai.service.Intent;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecutor;
import com.mokatest.platform.demos.ai.skill.DraftGenResult;
import com.mokatest.platform.demos.ai.skill.SkillRequest;
import com.mokatest.platform.demos.ai.stream.AiStreamBuffer;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.qa.ai.casegen.CaseDraftDTO;
import com.mokatest.platform.demos.qa.ai.casegen.CaseDraftMapper;
import com.mokatest.platform.demos.qa.ai.casegen.CaseGenPrompts;
import com.mokatest.platform.demos.qa.ai.casegen.CaseStreamParser;
import com.mokatest.platform.demos.qa.ai.casegen.DimensionBoardBuilder;
import com.mokatest.platform.demos.qa.ai.casegen.PointExtractor;
import com.mokatest.platform.demos.qa.ai.context.ExistingCaseSource;
import com.mokatest.platform.demos.qa.ai.context.HistoryBugSource;
import com.mokatest.platform.demos.qa.ai.context.ModuleRequirementSource;
import com.mokatest.platform.demos.qa.ai.context.RelatedRequirementSource;
import com.mokatest.platform.demos.qa.ai.context.RequirementCoreSource;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import com.mokatest.platform.demos.qa.service.TestCaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 生成测试用例服务（场景一）
 * <p>
 * 会话隔离：generate 创建新记录；append/adopt 只传 recordNo，
 * 锚定需求（entityId）与项目归属一律从记录读取，不信前端传参。
 * <p>
 * 重写后：用 LangChain4j CaseGenerationAssistant（AI Service + TokenStream + RAG）
 * 替代旧 AiSkill/SkillExecutionSupport/SkillRegistry 手工编排。上下文 L1-L5 组装内联，
 * 知识库 L0 由 AssistantFactory 的 ContentRetriever 自动注入，引用明细通过
 * TokenStream.onRetrieved 回调收集。
 */
@Service
public class AiCaseService {

    private static final Logger log = LoggerFactory.getLogger(AiCaseService.class);

    /**
     * SSE 心跳（流式 + extractCases 提取期间保持连接活跃）
     */
    private static final java.util.concurrent.ScheduledExecutorService HEARTBEAT =
            java.util.concurrent.Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "case-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    @Autowired
    private RequirementCoreSource requirementCoreSource;
    @Autowired
    private RelatedRequirementSource relatedRequirementSource;
    @Autowired
    private ModuleRequirementSource moduleRequirementSource;
    @Autowired
    private ExistingCaseSource existingCaseSource;
    @Autowired
    private HistoryBugSource historyBugSource;

    @Autowired
    private AssistantFactory assistantFactory;
    @Autowired
    private AiGenerationRecordService recordService;
    @Autowired
    private CaseDraftMapper caseDraftMapper;
    @Autowired
    private TestCaseService testCaseService;
    @Autowired
    private RequirementMapper requirementMapper;
    @Autowired
    private com.mokatest.platform.demos.ai.stream.AiStreamBufferRegistry bufferRegistry;
    @Autowired
    private com.mokatest.platform.demos.ai.knowledge.KnowledgeService knowledgeService;
    @Autowired
    private ModelManager modelManager;
    @Autowired
    private com.mokatest.platform.demos.qa.ai.casegen.SessionStatsBuilder sessionStatsBuilder;
    @Autowired
    private com.mokatest.platform.demos.qa.ai.casegen.CoverageAuditor coverageAuditor;
    @Autowired
    private com.mokatest.platform.demos.ai.memory.MysqlChatMemoryStore chatMemoryStore;

    // ==================== 场景一：流式生成 ====================

    @SuppressWarnings("unchecked")
    public void generateStream(SkillRequest request, boolean append, SseEmitter emitter) {
        String previousOutput = null;
        AiGenerationRecord record = null;

        if (append) {
            record = recordService.requireValid(request.getRecordNo(), request.getProjectId());
            request.setEntityId(record.getEntityId());
            request.setProjectId(record.getProjectId());
            previousOutput = latestRoundDrafts(record.getOutputSnapshot());
        }

        // 对话历史摘要注入（让模型感知用户修正与补充）
        if (record != null) {
            request.setHistory(CaseGenPrompts.buildHistory(
                    recordService.parseRounds(record.getOutputSnapshot())));
        }

        List<String> trimmedNotes = new ArrayList<>();
        String contextText = buildContextText(request, trimmedNotes);

        // 发送即落库：新会话先建记录；意图路由决定本轮走问答还是生成
        if (record == null) {
            record = createRecord(request, CaseGenPrompts.SCENE_CASE, trimmedNotes);
            request.setRecordNo(record.getRecordNo());
        }
        // 必须先 create 再 beginRound：create 会取消同 key 旧 buffer，
        // 其 cancel handle 的 stopRound 作用于"最新 GENERATING 轮次"，
        // 若先 beginRound，新轮次会被旧流的取消误标 STOPPED
        final AiStreamBuffer buffer = bufferRegistry.create(record.getRecordNo());
        try {
            recordService.beginRound(record.getRecordNo(), request.getProjectId(), request.getInstruction(), "gen");
        } catch (Exception e) {
            log.info("强制终止卡住的生成轮次 | recordNo={} err={}", record.getRecordNo(), e.getMessage());
            recordService.failRound(record.getRecordNo(), request.getProjectId(), "上一轮超时未完成，自动终止", null);
            recordService.beginRound(record.getRecordNo(), request.getProjectId(), request.getInstruction(), "gen");
        }
        Intent intent;
        try {
            intent = assistantFactory.createClassifier().classify(request.getInstruction());
        } catch (Exception e) {
            log.warn("意图分类失败，按生成处理: {}", e.getMessage());
            intent = Intent.GENERATE;
        }

        final AiGenerationRecord finalRecord = record;
        final String recordNo = record.getRecordNo();
        final long[] lastFlush = {System.currentTimeMillis()};

        if (intent == Intent.QA) {
            qaStream(request, contextText, finalRecord, buffer, emitter);
            return;
        }

        int count = (request.getCount() != null && request.getCount() > 0)
                ? request.getCount()
                : CaseGenPrompts.resolveCount(request.getInstruction(), null, 5);
        String userMsg = CaseGenPrompts.buildCaseGenUserMessage(
                contextText, request.getHistory(), request.getInstruction(), previousOutput, count);

        CaseGenerationAssistant assistant = assistantFactory.createCaseGenerator(
                request.getProjectId(), CaseGenPrompts.caseGenSystemPrompt(), 800 + count * 300);
        // 阶段通知：上下文组装完成，模型生成开始
        send(emitter, "stage", "{\"stage\":\"gen\"}");
        runStream(assistant.generate(userMsg), buffer, emitter, recordNo, request.getProjectId(),
                request.getCitations(),
                full -> handleComplete(emitter, request, finalRecord, buffer, full));
    }

    // ==================== 问答轮 ====================

    private void qaStream(SkillRequest request, String contextText, AiGenerationRecord record,
                          AiStreamBuffer buffer, SseEmitter emitter) {
        String recordNo = record.getRecordNo();
        // 构建会话状态统计块（确定性数据注入，模型读数回答）
        String sessionStats = sessionStatsBuilder.build(recordNo, request.getProjectId(), request.getEntityId());
        String userMsg = CaseGenPrompts.buildQaUserMessage(
                contextText, request.getHistory(), request.getInstruction(), sessionStats);

        // §3 Agent 拆分：QA 使用独立 CaseQaAssistant（LangChain4j AI Service）
        CaseQaAssistant assistant = assistantFactory.createCaseQaAssistant(request.getProjectId());
        // 标记本轮为问答轮（前端据此区分 delta 写 content 还是 _rawText）
        send(emitter, "stage", "{\"stage\":\"qa\"}");
        runStream(assistant.chat(userMsg), buffer, emitter, recordNo, request.getProjectId(),
                request.getCitations(),
                full -> {
                    recordService.finishQaRound(recordNo, request.getProjectId(), full);
                    Map<String, Object> result = new HashMap<>();
                    result.put("recordNo", recordNo);
                    result.put("type", "qa");
                    result.put("drafts", List.of());
                    String resultJson = JSON.toJSONString(result);
                    buffer.finish(resultJson);
                    send(emitter, "result", resultJson);
                    emitter.complete();
                });
    }

    // ==================== Phase 4：四阶段生成 ====================

    public void pointsStream(SkillRequest request, SseEmitter emitter) {
        log.info("[Phase4] 设计维度点生成开始 | reqId={} projectId={}", request.getEntityId(), request.getProjectId());

        List<String> trimmedNotes = new ArrayList<>();
        String contextText = buildContextText(request, trimmedNotes);

        AiGenerationRecord record = createRecord(request, CaseGenPrompts.SCENE_POINTS, trimmedNotes);
        request.setRecordNo(record.getRecordNo());
        recordService.updateStage(record.getRecordNo(), request.getProjectId(), "POINTS_GEN", null);
        // 提前下发 recordNo：流式期间前端「停止生成」需要它（done 事件才带就太晚了）
        send(emitter, "record", "{\"recordNo\":\"" + record.getRecordNo() + "\"}");

        final String recordNo = record.getRecordNo();
        String userMsg = CaseGenPrompts.buildPointsUserMessage(contextText, request.getInstruction());

        CaseGenerationAssistant assistant = assistantFactory.createCaseGenerator(
                request.getProjectId(), CaseGenPrompts.POINTS_SYSTEM_PROMPT);
        final AiStreamBuffer buffer = bufferRegistry.create(recordNo);
        runStream(assistant.generate(userMsg), buffer, emitter, recordNo, request.getProjectId(),
                request.getCitations(),
                full -> {
                    try {
                        // fastjson 对空串返回 null 而不抛异常，须先显式判空
                        if (full == null || full.isBlank()) {
                            throw new BusinessException("模型未返回正文内容（可能 token 上限被思考过程占满），请重新生成");
                        }
                        // pointsStream 用 generate()（无 OutputGuardrails），直接解析
                        com.alibaba.fastjson.JSONObject parsed = JSON.parseObject(full);
                        if (parsed == null) {
                            throw new BusinessException("模型输出为空或不是 JSON 对象");
                        }
                        com.alibaba.fastjson.JSONArray testPoints = parsed.getJSONArray("test_points");
                        if (testPoints == null || testPoints.isEmpty()) {
                            throw new BusinessException("模型输出缺少 test_points 数组");
                        }
                        com.alibaba.fastjson.JSONObject payload = new com.alibaba.fastjson.JSONObject();
                        payload.put("testPoints", testPoints);
                        recordService.updateStage(recordNo, request.getProjectId(), "POINTS_CONFIRM", payload.toJSONString());
                        com.alibaba.fastjson.JSONObject result = new com.alibaba.fastjson.JSONObject();
                        result.put("recordNo", recordNo);
                        result.put("testPoints", testPoints);
                        send(emitter, "done", result.toJSONString());
                    } catch (Exception e) {
                        log.warn("设计维度点解析失败 | recordNo={} err={}", recordNo, e.getMessage());
                        recordService.updateStage(recordNo, request.getProjectId(), "FAILED", null);
                        send(emitter, "error", "设计维度点解析失败：" + e.getMessage());
                    }
                    safeComplete(emitter);
                });
    }

    public void saveTestPoints(String recordNo, Integer projectId, String testPointsJson) {
        AiGenerationRecord record = recordService.requireValid(recordNo, projectId);
        String stage = record.getCurrentStage();
        if ("POINTS_CONFIRM".equals(stage) || "POINTS_GEN".equals(stage) || "FAILED".equals(stage)) {
            com.alibaba.fastjson.JSONObject payload = new com.alibaba.fastjson.JSONObject();
            payload.put("testPoints", JSON.parse(testPointsJson));
            recordService.updateStage(recordNo, projectId, "POINTS_CONFIRM", payload.toJSONString());
        } else {
            throw new BusinessException("当前阶段不允许保存设计维度点");
        }
    }

    @SuppressWarnings("unchecked")
    public void casesStream(SkillRequest request, SseEmitter emitter) {
        long phaseStart = System.currentTimeMillis();
        log.info("[性能] === 用例生成全链路开始 === | reqId={} projectId={}", request.getEntityId(), request.getProjectId());
        String recordNo = request.getRecordNo();
        AiGenerationRecord record = null;
        if (recordNo != null && !recordNo.isBlank()) {
            try {
                record = recordService.requireValid(recordNo, request.getProjectId());
                request.setEntityId(record.getEntityId());
                request.setProjectId(record.getProjectId());
            } catch (BusinessException e) {
                log.info("生成记录已过期，自动创建新记录 | oldRecordNo={}", recordNo);
                AiGenerationRecord expired = recordService.getByRecordNo(recordNo, request.getProjectId());
                if (expired != null) {
                    request.setEntityId(expired.getEntityId());
                    request.setProjectId(expired.getProjectId());
                }
                record = null;
            }
        }
        if (record == null) {
            if (request.getEntityId() == null) {
                send(emitter, "error", "缺少需求ID");
                safeComplete(emitter);
                return;
            }
            record = createRecord(request, CaseGenPrompts.SCENE_CASE, new ArrayList<>());
            recordNo = record.getRecordNo();
            request.setRecordNo(recordNo);
            send(emitter, "record", "{\"recordNo\":\"" + recordNo + "\"}");
        }
        long tRecord = System.currentTimeMillis();
        log.info("[性能] 阶段1-记录校验/创建 | 耗时={}ms", tRecord - phaseStart);

        recordService.updateStage(recordNo, request.getProjectId(), "CASE_GEN", null);

        // 对话历史
        java.util.List<java.util.Map<String, Object>> rounds = recordService.parseRounds(record.getOutputSnapshot());
        request.setHistory(CaseGenPrompts.buildHistory(rounds));

        List<String> trimmedNotes = new ArrayList<>();
        String contextText = buildContextText(request, trimmedNotes);

        // 续跑检测：最后一轮 STOPPED 且有 drafts → 加载 previousOutput，跳过意图分类
        String previousOutput = null;
        boolean isResume = false;
        int existingCount = 0;
        if (!rounds.isEmpty()) {
            java.util.Map<String, Object> lastRound = rounds.get(rounds.size() - 1);
            if ("STOPPED".equals(String.valueOf(lastRound.get("status")))) {
                Object draftsObj = lastRound.get("drafts");
                if (draftsObj != null) {
                    previousOutput = JSON.toJSONString(draftsObj);
                    isResume = true;
                    existingCount = draftsObj instanceof java.util.List ? ((java.util.List<?>) draftsObj).size() : 0;
                }
            }
        }

        String origInstruction = request.getInstruction() != null ? request.getInstruction() : "";
        String roundInstruction = origInstruction;
        // 单维度对话：保留用户原文指令（由 generateForTargetTpId 内部做意图路由），
        // 不再覆盖为硬编码的"生成测试用例"指令（否则所有消息都会被误判为生成路径）
        if (request.getTargetTpId() != null && !request.getTargetTpId().isBlank()) {
            String tpTitle = findTestPointTitle(record, request.getTargetTpId());
            roundInstruction = "逐点对话：" + origInstruction;
            isResume = false;
        }
        // #17: tpId 编码到 instruction 前缀，供 restoreFromRecord 按测试点分组
        if (request.getTargetTpId() != null && !request.getTargetTpId().isBlank()) {
            roundInstruction = "[TP:" + request.getTargetTpId() + "]" + roundInstruction;
        }
        if (isResume) {
            origInstruction = "上一轮生成被中断，已生成以下 " + existingCount + " 条用例。请继续补充剩余用例，不要重复已生成的。";
        }
        // 追加生成时：加载该需求下已有用例名拼入 previousOutput（去重清单，不受历史摘要截断限制）
        if (!isResume && record != null && request.getEntityId() != null) {
            List<TestCase> existingCases = testCaseService.list(
                    new QueryWrapper<TestCase>().eq("project_id", request.getProjectId())
                            .eq("requirement_id", request.getEntityId()).select("id", "case_name"));
            if (!existingCases.isEmpty()) {
                StringBuilder dedupList = new StringBuilder("\n## 已有用例名称清单（以下用例已存在，不要重复生成）\n");
                for (TestCase c : existingCases) {
                    if (c.getCaseName() != null) dedupList.append("- ").append(c.getCaseName()).append('\n');
                }
                previousOutput = dedupList.toString();
            }
        }
        // 追加轮次注入未覆盖维度点（从 stagePayload 恢复覆盖矩阵，定向瞄准；单维度重生跳过）
        if (request.getTargetTpId() == null && record.getStagePayload() != null) {
            try {
                com.alibaba.fastjson.JSONObject payload = JSON.parseObject(record.getStagePayload());
                com.alibaba.fastjson.JSONArray uncovered = payload.getJSONArray("uncovered");
                if (uncovered != null && !uncovered.isEmpty()) {
                    StringBuilder aimBuilder = new StringBuilder("\n## 尚未覆盖的维度点（本轮请优先生成覆盖这些维度的用例）\n");
                    for (int i = 0; i < uncovered.size(); i++) {
                        com.alibaba.fastjson.JSONObject tp = uncovered.getJSONObject(i);
                        if (tp != null) {
                            aimBuilder.append("- ").append(tp.getString("id")).append(": ").append(tp.getString("title")).append('\n');
                        }
                    }
                    String aimText = aimBuilder.toString();
                    if (previousOutput != null) {
                        previousOutput += aimText;
                    } else {
                        origInstruction += aimText;
                    }
                }
            } catch (Exception ignored) {}
        }
        int resolvedCount = CaseGenPrompts.resolveCount(origInstruction, request.getCount(), 5);
        request.setCount(resolvedCount);
        log.info("[性能] 条数解析 | instruction='{}' resolvedCount={} default=5 isResume={}", origInstruction, resolvedCount, isResume);

        String userMsg = CaseGenPrompts.buildCaseGenUserMessage(
                contextText, request.getHistory(), origInstruction, previousOutput, resolvedCount);
        long tContext = System.currentTimeMillis();
        log.info("[性能] 阶段2-上下文+消息构建 | 耗时={}ms userMsg字符={} 预估token={}",
                tContext - tRecord, userMsg.length(), com.mokatest.platform.demos.ai.context.ContextBlocks.estimateTokens(userMsg));

        // #16: 逐点对话时 buffer key 含 tpId，避免不同 TP 的 buffer 串
        // 注意：必须先 create 再 beginRound——create 会取消同 key 旧 buffer，
        // 其 cancel handle 的 stopRound 作用于"最新 GENERATING 轮次"，
        // 若先 beginRound，新轮次会被旧流的取消误标 STOPPED
        final String bufferKey = (request.getTargetTpId() != null && !request.getTargetTpId().isBlank())
                ? recordNo + "_" + request.getTargetTpId() : recordNo;
        final AiStreamBuffer buffer = bufferRegistry.create(bufferKey);

        try {
            recordService.beginRound(recordNo, request.getProjectId(), roundInstruction, "gen");
        } catch (Exception e) {
            log.info("强制终止卡住的生成轮次 | recordNo={} err={}", recordNo, e.getMessage());
            recordService.failRound(recordNo, request.getProjectId(), "上一轮超时未完成，自动终止", null);
            recordService.beginRound(recordNo, request.getProjectId(), roundInstruction, "gen");
        }
        long tRound = System.currentTimeMillis();
        log.info("[性能] 阶段3-轮次初始化 | 耗时={}ms", tRound - tContext);

        final AiGenerationRecord finalRecord = record;
        final String finalRecordNo = recordNo;

        // 测试点驱动模式：不做意图分类，直接走生成

        // ==================== V2 逐点任务制生成 ====================
        final String finalContextText = contextText;
        final String finalOrigInstruction = origInstruction;

        if (request.getTargetTpId() != null && !request.getTargetTpId().isBlank()) {
            // 单维度重生：同步生成该维度点的用例，完成后走自动审查
            generateForTargetTpId(request, emitter, buffer, finalRecord, finalContextText, finalOrigInstruction);
        } else {
            // V2 任务队列：提取测试点 → 去重 → 逐点生成 → 自动审查 → 收尾
            startV2TaskQueue(request, emitter, buffer, finalRecord, finalContextText, finalOrigInstruction);
        }
    }

    // ==================== V1 完成处理（generateStream 用，保留） ====================

    @SuppressWarnings("unchecked")
    private void handleComplete(SseEmitter emitter, SkillRequest request, AiGenerationRecord record,
                                AiStreamBuffer buffer, String full) {
        String recordNo = record.getRecordNo();
        // 收尾写入前最后防线：流完成后、finishRound 前收到 stop 时，标 STOPPED 而非 DONE
        if (handleCancelled(recordNo, request.getProjectId(), buffer, emitter)) {
            return;
        }
        try {
            // 阶段通知：结构化解析
            send(emitter, "stage", "{\"stage\":\"parse\"}");
            // @OutputGuardrails 已验证 JSON 合法性并自动 reprompt，这里直接解析
            DraftGenResult<CaseDraftDTO> genResult = parseCaseGenResult(full);
            List<CaseDraftDTO> drafts = genResult.getDrafts();
            assignDraftIds(drafts);
            // 阶段通知：入库校验
            send(emitter, "stage", "{\"stage\":\"dedup\"}");
            recordService.finishRound(recordNo, request.getProjectId(),
                    JSON.toJSONString(drafts), full, JSON.toJSONString(genResult.getUncertainties()),
                    JSON.toJSONString(request.getCitations()));
            knowledgeService.settleCitations(request.getCitations());

            Map<String, Object> result = new HashMap<>();
            result.put("recordNo", recordNo);
            result.put("drafts", drafts);
            result.put("uncertainties", genResult.getUncertainties());
            result.put("citations", request.getCitations());
            String resultJson = JSON.toJSONString(result);
            buffer.finish(resultJson);
            send(emitter, "result", resultJson);
            emitter.complete();
        } catch (Exception e) {
            log.warn("AI 生成用例解析失败: {}", e.getMessage());
            recordService.failRound(recordNo, request.getProjectId(),
                    e instanceof BusinessException ? e.getMessage() : "生成结果解析失败", buffer.text());
            buffer.fail(e.getMessage());
            send(emitter, "error", e instanceof BusinessException ? e.getMessage() : "生成结果解析失败，请重试");
            emitter.complete();
        }
    }

    // ==================== V2 逐点任务制 ====================
    
    /**
     * V2 任务队列入口：提取测试点 → 去重 → 逐点生成 → 自动审查 → 收尾。
     */
    private void startV2TaskQueue(SkillRequest request, SseEmitter emitter, AiStreamBuffer buffer,
                                   AiGenerationRecord record, String contextText, String instruction) {
        String recordNo = record.getRecordNo();
        Integer projectId = request.getProjectId();
    
        // 检查 stagePayload 是否已有 testPoints
        List<Map<String, Object>> testPoints = loadTestPointsFromPayload(record);
    
        if (testPoints == null || testPoints.isEmpty()) {
            // 提取测试点（流式，用户可见进度）
            send(emitter, "stage", "{\"stage\":\"context\"}");
            CaseGenerationAssistant extractor = assistantFactory.createCaseGenerator(
                    projectId, CaseGenPrompts.POINTS_SYSTEM_PROMPT);
            String userMsg = CaseGenPrompts.buildPointsUserMessage(contextText, instruction);
            final int baseOffset = buffer.length();
            runStream(extractor.generate(userMsg), buffer, emitter, recordNo, projectId,
                    request.getCitations(),
                    full -> {
                        try {
                            com.alibaba.fastjson.JSONObject parsed = JSON.parseObject(full);
                            if (parsed == null) throw new BusinessException("测试点提取输出为空");
                            com.alibaba.fastjson.JSONArray tpArr = parsed.getJSONArray("test_points");
                            // Fallback: thinking 模式下 AI 可能直接输出 test_cases 而非 test_points
                            com.alibaba.fastjson.JSONArray caseArr = parsed.getJSONArray("test_cases");
                            if ((tpArr == null || tpArr.isEmpty()) && caseArr != null && !caseArr.isEmpty()) {
                                log.info("[V2] AI 未输出 test_points，从 test_cases 推断测试点并直接采纳用例");
                                handleCasesAsPoints(request, emitter, buffer, record, full, recordNo, projectId);
                                return;
                            }
                            if (tpArr == null || tpArr.isEmpty())
                                throw new BusinessException("模型未输出 test_points");
                            List<Map<String, Object>> tps = new java.util.ArrayList<>();
                            for (int i = 0; i < tpArr.size(); i++) {
                                tps.add(new java.util.LinkedHashMap<>(tpArr.getJSONObject(i)));
                            }
                            tps = PointExtractor.dedupTestPoints(tps);
                            // 按优先级截断：P0 > P1 > P2，上限 30
                            tps.sort((a, b) -> {
                                int pa = priorityOrder(String.valueOf(a.get("priority")));
                                int pb = priorityOrder(String.valueOf(b.get("priority")));
                                return Integer.compare(pa, pb);
                            });
                            if (tps.size() > 30) tps = tps.subList(0, 30);
                            for (Map<String, Object> tp : tps) {
                                tp.putIfAbsent("status", "PENDING");
                                tp.putIfAbsent("caseDraftIds", new java.util.ArrayList<String>());
                            }
                            persistTestPoints(recordNo, projectId, tps);
                            pushDimensions(emitter, tps, null, false);
                            runPerPointQueue(request, emitter, buffer, record, contextText, tps, null);
                        } catch (Exception e) {
                            log.error("测试点提取失败: {}", e.getMessage());
                            recordService.updateStage(recordNo, projectId, "FAILED", null);
                            buffer.fail(e.getMessage());
                            send(emitter, "error", "测试点提取失败：" + e.getMessage());
                            safeComplete(emitter);
                        }
                    }, null, baseOffset);
        } else {
            // 识别“全部重新生成”指令：重置所有 testPoints 为 PENDING + 清空 pool
            boolean regenerateAll = instruction != null && !instruction.isBlank()
                    && (instruction.contains("全部重新") || instruction.contains("重新生成全部")
                        || instruction.contains("全部重做") || instruction.contains("从头生成")
                        || instruction.contains("全部重来"));
            if (regenerateAll) {
                log.info("[V2] 用户指令识别为全部重新生成，清空 testPoints + pool 重新提取");
                // BUG#5 修复：不重置状态，而是清空 testPoints 让流程走重新提取分支
                com.alibaba.fastjson.JSONObject resetPayload = new com.alibaba.fastjson.JSONObject();
                resetPayload.put("pool", new com.alibaba.fastjson.JSONArray());
                recordService.updateStage(recordNo, projectId, "CASE_GEN", resetPayload.toJSONString());
                // 走 testPoints == null 的提取分支（与首次生成相同）
                send(emitter, "stage", "{\"stage\":\"context\"}");
                CaseGenerationAssistant extractor = assistantFactory.createCaseGenerator(
                        projectId, CaseGenPrompts.POINTS_SYSTEM_PROMPT);
                String userMsg = CaseGenPrompts.buildPointsUserMessage(contextText, instruction);
                final int baseOffset = buffer.length();
                runStream(extractor.generate(userMsg), buffer, emitter, recordNo, projectId,
                        request.getCitations(),
                        full -> {
                            try {
                                com.alibaba.fastjson.JSONObject parsed = JSON.parseObject(full);
                                if (parsed == null) throw new BusinessException("测试点提取输出为空");
                                com.alibaba.fastjson.JSONArray tpArr = parsed.getJSONArray("test_points");
                                // Fallback: thinking 模式下 AI 可能直接输出 test_cases 而非 test_points
                                com.alibaba.fastjson.JSONArray caseArr = parsed.getJSONArray("test_cases");
                                if ((tpArr == null || tpArr.isEmpty()) && caseArr != null && !caseArr.isEmpty()) {
                                    log.info("[V2] 全部重新生成-AI 未输出 test_points，从 test_cases 推断测试点并直接采纳用例");
                                    handleCasesAsPoints(request, emitter, buffer, record, full, recordNo, projectId);
                                    return;
                                }
                                if (tpArr == null || tpArr.isEmpty())
                                    throw new BusinessException("模型未输出 test_points");
                                List<Map<String, Object>> tps = new java.util.ArrayList<>();
                                for (int i = 0; i < tpArr.size(); i++) {
                                    tps.add(new java.util.LinkedHashMap<>(tpArr.getJSONObject(i)));
                                }
                                tps = PointExtractor.dedupTestPoints(tps);
                                tps.sort((a, b) -> Integer.compare(priorityOrder(String.valueOf(a.get("priority"))), priorityOrder(String.valueOf(b.get("priority")))));
                                if (tps.size() > 30) tps = tps.subList(0, 30);
                                for (Map<String, Object> tp : tps) {
                                    tp.putIfAbsent("status", "PENDING");
                                    tp.putIfAbsent("caseDraftIds", new java.util.ArrayList<String>());
                                }
                                persistTestPoints(recordNo, projectId, tps);
                                pushDimensions(emitter, tps, null, false);
                                runPerPointQueue(request, emitter, buffer, record, contextText, tps, null);
                            } catch (Exception e) {
                                log.error("全部重新生成-测试点提取失败: {}", e.getMessage());
                                recordService.updateStage(recordNo, projectId, "FAILED", null);
                                buffer.fail(e.getMessage());
                                send(emitter, "error", "测试点提取失败：" + e.getMessage());
                                safeComplete(emitter);
                            }
                        }, null, baseOffset);
                return;
            }
            
            // 已有 testPoints：识别用户指令是否针对某个维度的补充
            String matchedSupplement = null;
            if (instruction != null && !instruction.isBlank()) {
                String bestTpId = null;
                double bestScore = 0;
                for (Map<String, Object> tp : testPoints) {
                    String tpTitle = String.valueOf(tp.get("title"));
                    double score = PointExtractor.bigramJaccard(instruction, tpTitle);
                    if (score > bestScore) { bestScore = score; bestTpId = String.valueOf(tp.get("id")); }
                }
                if (bestScore >= 0.3 && bestTpId != null) {
                    // 识别为维度补充：重置该点为 PENDING（可能之前是 DONE），只跑这一个
                    for (Map<String, Object> tp : testPoints) {
                        String tpId = String.valueOf(tp.get("id"));
                        if (tpId.equals(bestTpId)) {
                            tp.put("status", "PENDING");
                            log.info("[V2] 用户指令匹配维度 {} (score={}), 重置为 PENDING 补充", tpId, String.format("%.2f", bestScore));
                        } else if ("PENDING".equals(String.valueOf(tp.getOrDefault("status", "PENDING")))) {
                            // 其他 PENDING 点暂不跑，只跑匹配的
                            tp.put("_skip", true);
                        }
                    }
                    matchedSupplement = instruction;
                    // 去掉 _skip 标记中的点
                    testPoints.removeIf(tp -> Boolean.TRUE.equals(tp.get("_skip")));
                }
            }
            pushDimensions(emitter, testPoints, null, false);
            runPerPointQueue(request, emitter, buffer, record, contextText, testPoints, matchedSupplement);
        }
    }
    
    /** 逐点任务队列：并发 2，单点 90s 超时，失败重试 1 次 */
    private void runPerPointQueue(SkillRequest request, SseEmitter emitter, AiStreamBuffer buffer,
                                   AiGenerationRecord record, String contextText,
                                   List<Map<String, Object>> testPoints, String userSupplement) {
        String recordNo = record.getRecordNo();
        Integer projectId = request.getProjectId();
        // 此方法可能从 runStream 回调线程调用（ThreadLocal 为空），创建 assistant 前必须恢复上下文
        ProjectContextHolder.setProjectId(projectId);
        com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.setTeamId(request.getTeamId());
        try {
        List<Map<String, Object>> pending = new java.util.ArrayList<>();
        for (Map<String, Object> tp : testPoints) {
            String st = String.valueOf(tp.getOrDefault("status", "PENDING"));
            if ("PENDING".equals(st)) pending.add(tp);
        }
        if (pending.isEmpty()) {
            afterAllPointsDone(request, emitter, buffer, record, testPoints);
            return;
        }

        String dedupList = buildDedupList(projectId, request.getEntityId(), record);
        CaseGenerationAssistant assistant = assistantFactory.createCaseGenerator(
                projectId, CaseGenPrompts.perPointSystemPrompt());

        java.util.concurrent.Semaphore sem = new java.util.concurrent.Semaphore(2);
        List<java.util.concurrent.CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        for (Map<String, Object> tp : pending) {
            final String tpId = String.valueOf(tp.get("id"));
            final String tpTitle = String.valueOf(tp.get("title"));
            futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                sem.acquireUninterruptibly();
                try {
                    ProjectContextHolder.setProjectId(projectId);
                    com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.setTeamId(request.getTeamId());
                    generateSinglePoint(tp, assistant, contextText, dedupList, emitter, buffer, recordNo, projectId, request.getEntityId(), record, testPoints, userSupplement);
                } finally {
                    ProjectContextHolder.clear();
                    com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.clear();
                    sem.release();
                }
            }).orTimeout(90, java.util.concurrent.TimeUnit.SECONDS).exceptionally(ex -> {
                // 超时或异常：标 FAILED，继续下一点
                tp.put("status", "FAILED");
                tp.put("failReason", "生成超时（90s）");
                pushDimensions(emitter, testPoints, null, false);
                send(emitter, "point_done", JSON.toJSONString(java.util.Map.of("tpId", tpId, "caseCount", 0, "failed", true)));
                log.warn("[V2] 点 {} 超时", tpId);
                return null;
            }));
        }
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                .thenRun(() -> {
                    ProjectContextHolder.setProjectId(projectId);
                    try {
                        afterAllPointsDone(request, emitter, buffer, record, testPoints);
                    } finally {
                        ProjectContextHolder.clear();
                    }
                });
        } finally {
            ProjectContextHolder.clear();
            com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.clear();
        }
    }
    
    /** 单点生成：同步调用 → 解析 → Layer2 去重 → 更新点状态 → 推 point_start/point_done */
    @SuppressWarnings("unchecked")
    private void generateSinglePoint(Map<String, Object> tp, CaseGenerationAssistant assistant,
                                      String contextText, String dedupList,
                                      SseEmitter emitter, AiStreamBuffer buffer,
                                      String recordNo, Integer projectId, Long entityId,
                                      AiGenerationRecord record, List<Map<String, Object>> allTestPoints,
                                      String userSupplement) {
        String tpId = String.valueOf(tp.get("id"));
        String tpTitle = String.valueOf(tp.get("title"));
        String tpCategory = String.valueOf(tp.getOrDefault("category", "FUNCTION"));
    
        // BUG#8 修复：超时可能已设 FAILED，跳过
        if ("FAILED".equals(String.valueOf(tp.get("status")))) return;
        // BUG#2 修复：检查 cancel 标志
        if (buffer.isCancelRequested()) {
            tp.put("status", "FAILED");
            tp.put("failReason", "用户已停止生成");
            return;
        }
        tp.put("status", "GENERATING");
        pushDimensions(emitter, allTestPoints, null, false);
        send(emitter, "point_start", JSON.toJSONString(java.util.Map.of("tpId", tpId, "title", tpTitle)));
    
        try {
            if (buffer.isCancelRequested()) { tp.put("status", "FAILED"); tp.put("failReason", "用户已停止"); return; }
            List<CaseDraftDTO> drafts = generateForPoint(tpId, tpTitle, tpCategory, contextText, dedupList, assistant, userSupplement, 5);
            assignDraftIds(drafts);
            int removed = layer2Dedup(drafts, projectId, entityId, recordNo);
            // "cancel 检查 + 写草稿池"按 recordNo 加锁原子化：
            // stop 的收尾写库与这里的落库互斥，已取消绝不写草稿
            synchronized (recordLock(recordNo)) {
                if (buffer.isCancelRequested()) { tp.put("status", "FAILED"); tp.put("failReason", "用户已停止"); return; }
                // BUG#3 修复：用同步方法 addDraftsToPoolSync 替代手动 load-add-save，防并发竞态
                addDraftsToPoolSync(recordNo, projectId, drafts, allTestPoints);
            }
            List<String> draftIds = new java.util.ArrayList<>();
            for (CaseDraftDTO d : drafts) draftIds.add(d.getDraftId());
            tp.put("status", "DONE");
            tp.put("caseDraftIds", draftIds);
            pushDimensions(emitter, allTestPoints, null, false);
            send(emitter, "point_done", JSON.toJSONString(java.util.Map.of("tpId", tpId, "caseCount", drafts.size())));
            for (CaseDraftDTO d : drafts) send(emitter, "case", JSON.toJSONString(d));
            log.info("[V2] 点 {} 完成：{} 条用例，去重 {} 条", tpId, drafts.size(), removed);
        } catch (Exception e) {
            log.warn("[V2] 点 {} 首次失败，重试 1 次: {}", tpId, e.getMessage());
            try {
                if (buffer.isCancelRequested()) { tp.put("status", "FAILED"); tp.put("failReason", "用户已停止"); return; }
                List<CaseDraftDTO> drafts = generateForPoint(tpId, tpTitle, tpCategory, contextText, dedupList, assistant, userSupplement, 5);
                assignDraftIds(drafts);
                layer2Dedup(drafts, projectId, entityId, recordNo);
                // 重试路径同样："cancel 检查 + 写草稿池"加锁原子化
                synchronized (recordLock(recordNo)) {
                    if (buffer.isCancelRequested()) { tp.put("status", "FAILED"); tp.put("failReason", "用户已停止"); return; }
                    addDraftsToPoolSync(recordNo, projectId, drafts, allTestPoints);
                }
                List<String> draftIds = new java.util.ArrayList<>();
                for (CaseDraftDTO d : drafts) draftIds.add(d.getDraftId());
                tp.put("status", "DONE");
                tp.put("caseDraftIds", draftIds);
                pushDimensions(emitter, allTestPoints, null, false);
                send(emitter, "point_done", JSON.toJSONString(java.util.Map.of("tpId", tpId, "caseCount", drafts.size())));
                for (CaseDraftDTO d : drafts) send(emitter, "case", JSON.toJSONString(d));
            } catch (Exception e2) {
                // BUG#7 修复：用 e2（重试异常）而非 e（首次异常）
                tp.put("status", "FAILED");
                tp.put("failReason", e2.getMessage());
                updatePointStatusInPayload(recordNo, projectId, allTestPoints, loadPoolArr(recordNo, projectId));
                pushDimensions(emitter, allTestPoints, null, false);
                send(emitter, "point_done", JSON.toJSONString(java.util.Map.of("tpId", tpId, "caseCount", 0, "failed", true)));
                log.error("[V2] 点 {} 重试仍失败: {}", tpId, e2.getMessage());
            }
        }
    }
    
    /** 同步调用 AI Service 生成单点用例（langchain4j @OutputGuardrails 自动验证 JSON） */
    private List<CaseDraftDTO> generateForPoint(String tpId, String tpTitle, String tpCategory,
                                                  String contextText, String dedupList,
                                                  CaseGenerationAssistant assistant, String userSupplement,
                                                  int count) {
        String userMsg = CaseGenPrompts.buildPerPointUserMessage(tpId, tpTitle, tpCategory, contextText, dedupList, count, userSupplement);
        String full = assistant.generateCasesSync(userMsg);
        DraftGenResult<CaseDraftDTO> genResult = parseCaseGenResult(full);
        if (genResult.getDrafts().isEmpty()) {
            throw new BusinessException("模型为维度点 " + tpId + " 未生成有效用例");
        }
        return genResult.getDrafts();
    }
    
    /** 单维度对话（关键词路由：生成→同步生成，其他→流式QA） */
    private void generateForTargetTpId(SkillRequest request, SseEmitter emitter, AiStreamBuffer buffer,
                                        AiGenerationRecord record, String contextText, String instruction) {
        String recordNo = record.getRecordNo();
        Integer projectId = request.getProjectId();
        String tpId = request.getTargetTpId();
        String tpTitle = findTestPointTitle(record, tpId);
        String tpCategory = findTestPointCategory(record, tpId);

        // IntentClassifier 语义理解意图（langchain4j Tutorial 06），默认 QA（安全降级）
        Intent intent = Intent.QA;
        try {
            intent = assistantFactory.createClassifier().classify(instruction);
        } catch (Exception e) {
            log.warn("[逐点] 意图分类失败，默认 QA: {}", e.getMessage());
        }
        log.info("[逐点] instruction='{}' intent={}", instruction, intent);
        if (intent != Intent.GENERATE) {
            qaForTargetTpId(request, emitter, buffer, record, contextText, instruction, tpId, tpTitle, tpCategory);
            return;
        }

        // ===== 生成路径 =====
        // 同步路径全程无流式回调，cancel 检查必须覆盖每个写入点之前
        if (handleCancelled(recordNo, projectId, buffer, emitter)) {
            return;
        }
        boolean isRegenerate = instruction != null && instruction.contains("重新生成");
        int pointDraftCount = getPointDraftCount(recordNo, projectId, tpId);
        String userSupplement = instruction;
        if (pointDraftCount > 0 && !isRegenerate) {
            userSupplement = "该测试点已有 " + pointDraftCount + " 条草稿，请在此基础上补充，不要重复已有用例。\n" + instruction;
        }
        send(emitter, "stage", "{\"stage\":\"gen\"}");
        send(emitter, "point_start", JSON.toJSONString(java.util.Map.of("tpId", tpId, "title", tpTitle)));
        String dedupList = buildPerPointDedupList(projectId, request.getEntityId(), record, tpId);
        CaseGenerationAssistant assistant = assistantFactory.createCaseGenerator(
                projectId, CaseGenPrompts.perPointSystemPrompt());
        java.util.concurrent.ScheduledFuture<?> hb = HEARTBEAT.scheduleAtFixedRate(() -> {
            try { emitter.send(SseEmitter.event().comment("hb")); } catch (Exception ignored) {}
        }, 5, 5, java.util.concurrent.TimeUnit.SECONDS);
        // 绑定"尽力中断"取消句柄：同步 ChatModel 底层是 okhttp 阻塞式 execute()，
        // 线程 interrupt 无法断开 socket 读，只能尽力唤醒；服务端生成仍会继续（token 照常计费）。
        // 真正保证"取消后不写 DONE/草稿"的是下方各写入点前的 isCancelRequested 检查。
        final Thread workerThread = Thread.currentThread();
        buffer.bindCancel(() -> {
            workerThread.interrupt();
            hb.cancel(false);
            // 与工作线程的"检查+finishRound"按 recordNo 互斥
            synchronized (recordLock(recordNo)) {
                try {
                    recordService.stopRound(recordNo, projectId, null);
                    recordService.updateStage(recordNo, projectId, "STOPPED", null);
                } catch (Exception e) {
                    log.warn("[停止] 逐点生成取消收尾写库失败 | recordNo={} err={}", recordNo, e.getMessage());
                }
            }
            buffer.markStopped();
            send(emitter, "stopped", "已停止");
            safeComplete(emitter);
        });
        try {
            ProjectContextHolder.setProjectId(projectId);
            int count = CaseGenPrompts.resolveCount(instruction, null, 5);
            if (handleCancelled(recordNo, projectId, buffer, emitter)) {
                return;
            }
            List<CaseDraftDTO> drafts = generateForPoint(tpId, tpTitle, tpCategory, contextText, dedupList, assistant, userSupplement, count);
            // LLM 同步调用返回后立即检查：已取消则不写草稿池、不 finishRound
            if (handleCancelled(recordNo, projectId, buffer, emitter)) {
                return;
            }
            assignDraftIds(drafts);
            layer2Dedup(drafts, projectId, request.getEntityId(), recordNo);
            com.alibaba.fastjson.JSONArray poolArr = loadPoolArr(recordNo, projectId);
            com.alibaba.fastjson.JSONArray cleanedPool;
            if (isRegenerate) {
                // 覆盖模式：清理该维度旧草稿
                cleanedPool = new com.alibaba.fastjson.JSONArray();
                for (int i = 0; i < poolArr.size(); i++) {
                    com.alibaba.fastjson.JSONObject d = poolArr.getJSONObject(i);
                    com.alibaba.fastjson.JSONArray cps = d != null ? d.getJSONArray("coverPoints") : null;
                    boolean belongsToThisTp = false;
                    if (cps != null) {
                        for (int j = 0; j < cps.size(); j++) {
                            if (tpId.equals(String.valueOf(cps.get(j)))) { belongsToThisTp = true; break; }
                        }
                    }
                    if (!belongsToThisTp) cleanedPool.add(d);
                }
            } else {
                // 追加模式：保留旧草稿
                cleanedPool = poolArr;
            }
            for (CaseDraftDTO d : drafts) cleanedPool.add(JSON.toJSON(d));
            // "cancel 检查 + finishRound"按 recordNo 加锁原子化，与 stop 收尾互斥：
            // 已取消的轮次只能标 STOPPED，绝不写 DONE、绝不写草稿
            synchronized (recordLock(recordNo)) {
                if (handleCancelled(recordNo, projectId, buffer, emitter)) {
                    return;
                }
                recordService.finishRound(recordNo, projectId,
                        JSON.toJSONString(drafts), null, null, null);
            }
            knowledgeService.settleCitations(request.getCitations());
            Map<String, Object> audit = coverageAuditor.auditSinglePoint(recordNo, projectId, request.getEntityId(), tpId);
            List<Map<String, Object>> testPoints = loadTestPointsFromPayload(record);
            // 更新该测试点状态为 DONE + 记录 caseDraftIds，持久化到 stagePayload
            if (testPoints != null) {
                for (Map<String, Object> tp : testPoints) {
                    if (tpId.equals(String.valueOf(tp.get("id")))) {
                        tp.put("status", "DONE");
                        List<String> draftIds = new ArrayList<>();
                        for (CaseDraftDTO d : drafts) {
                            if (d.getDraftId() != null) draftIds.add(d.getDraftId());
                        }
                        tp.put("caseDraftIds", draftIds);
                        break;
                    }
                }
                updatePointStatusInPayload(recordNo, projectId, testPoints, cleanedPool);
            }
            send(emitter, "point_done", JSON.toJSONString(java.util.Map.of("tpId", tpId, "caseCount", drafts.size())));
            send(emitter, "audit", JSON.toJSONString(audit));
            Map<String, Object> result = new HashMap<>();
            result.put("recordNo", recordNo);
            result.put("type", "point");
            result.put("tpId", tpId);
            result.put("audit", audit);
            com.alibaba.fastjson.JSONArray cleanPool = new com.alibaba.fastjson.JSONArray();
            java.util.Set<String> adoptedIds = recordService.parseAdoptedDraftIds(recordNo);
            for (int i = 0; i < cleanedPool.size(); i++) {
                com.alibaba.fastjson.JSONObject d = cleanedPool.getJSONObject(i);
                String draftId = d != null ? d.getString("draftId") : null;
                if (draftId != null && adoptedIds.contains(draftId)) continue;
                cleanPool.add(d);
            }
            result.put("poolDrafts", cleanPool);
            result.put("pointCaseCount", getPointDraftCount(recordNo, projectId, tpId));
            result.put("dimensions", DimensionBoardBuilder.build(testPoints != null ? testPoints : java.util.List.of(), cleanPool, audit, false));
            String resultJson = JSON.toJSONString(result);
            buffer.finish(resultJson);
            send(emitter, "result", resultJson);
            safeComplete(emitter);
        } catch (Exception e) {
            if (buffer.isCancelRequested()) {
                // 取消期间的异常（中断/连接关闭等）不得把 STOPPED 轮次覆盖为 FAILED
                handleCancelled(recordNo, projectId, buffer, emitter);
                return;
            }
            log.error("[V2] 单维度重生失败: {}", e.getMessage());
            recordService.failRound(recordNo, projectId, e.getMessage(), null);
            buffer.fail(e.getMessage());
            send(emitter, "error", "生成失败：" + e.getMessage());
            safeComplete(emitter);
        } finally {
            hb.cancel(false);
            ProjectContextHolder.clear();
        }
    }

    /** 逐点 QA：ChatMemory 可追问 + 需求摘要 + 已有用例名，无 RAG 防 JSON */
    private void qaForTargetTpId(SkillRequest request, SseEmitter emitter, AiStreamBuffer buffer,
                                   AiGenerationRecord record, String contextText, String instruction,
                                   String tpId, String tpTitle, String tpCategory) {
        String recordNo = record.getRecordNo();
        Integer projectId = request.getProjectId();
        Long entityId = request.getEntityId();
        String dedupList = buildPerPointDedupList(projectId, entityId, record, tpId);
        int caseCount = getPointDraftCount(recordNo, projectId, tpId);
        // 简单问题直接返回硬编码回复，不调 LLM
        String quickReply = quickReply(instruction, caseCount);
        if (quickReply != null) {
            send(emitter, "stage", "{\"stage\":\"qa\"}");
            send(emitter, "delta", quickReply);
            recordService.finishQaRound(recordNo, projectId, quickReply);
            Map<String, Object> result = new HashMap<>();
            result.put("recordNo", recordNo);
            result.put("type", "qa");
            result.put("tpId", tpId);
            result.put("drafts", List.of());
            String resultJson = JSON.toJSONString(result);
            buffer.finish(resultJson);
            send(emitter, "result", resultJson);
            safeComplete(emitter);
            return;
        }
        // 复杂问题走 LLM + ChatMemory
        String memoryId = "casepoint_v2_" + recordNo + "_" + tpId;
        // 构建 system prompt：角色 + 测试点信息 + 已有用例名 + 需求摘要(L1 only)
        StringBuilder sysPrompt = new StringBuilder();
        sysPrompt.append("你是测试设计问答助手，围绕当前测试点与用户对话。\n");
        sysPrompt.append("回答必须使用中文自然语言，可以使用 markdown 列表。\n");
        sysPrompt.append("禁止输出 JSON、代码块等任何编程格式数据。\n");
        sysPrompt.append("只回答与测试用例设计相关的问题。与测试无关的问题，礼貌拒绝。\n");
        sysPrompt.append("只讨论当前测试点，不涉及其他测试点或维度。\n\n");
        sysPrompt.append("## 当前测试点\n");
        sysPrompt.append("标题：").append(tpTitle).append('\n');
        sysPrompt.append("分类：").append(tpCategory).append('\n');
        sysPrompt.append("已有 ").append(caseCount).append(" 条草稿。\n");
        if (dedupList != null && !dedupList.isEmpty()) {
            sysPrompt.append("\n## 已有用例\n").append(dedupList);
        }
        // L1 需求摘要（不含 L4 已有用例样例，防弱模型输出 JSON）
        try {
            Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                    .eq("id", entityId).eq("project_id", projectId));
            if (req != null) {
                ContextBlocks.Block l1 = requirementCoreSource.load(req);
                if (l1 != null && l1.content() != null) {
                    sysPrompt.append("\n## 需求摘要\n").append(l1.content()).append('\n');
                }
            }
        } catch (Exception ignored) {}
        String userMsg = CaseGenPrompts.buildQaUserMessage("", null, instruction, null);
        send(emitter, "stage", "{\"stage\":\"qa\"}");
        CasePointQaAssistant assistant = assistantFactory.createCasePointQaAssistant(sysPrompt.toString());
        runStream(assistant.chat(memoryId, userMsg), buffer, emitter, recordNo, projectId,
                request.getCitations(),
                full -> {
                    recordService.finishQaRound(recordNo, projectId, full);
                    Map<String, Object> result = new HashMap<>();
                    result.put("recordNo", recordNo);
                    result.put("type", "qa");
                    result.put("tpId", tpId);
                    result.put("drafts", List.of());
                    String resultJson = JSON.toJSONString(result);
                    buffer.finish(resultJson);
                    send(emitter, "result", resultJson);
                    emitter.complete();
                });
    }

    /** 简单问题快速回复（不调 LLM），返回 null 表示需要走 LLM */
    private String quickReply(String instruction, int caseCount) {
        if (instruction == null) return null;
        if (instruction.contains("几个") || instruction.contains("多少")) {
            return caseCount > 0
                ? "当前测试点已生成 " + caseCount + " 条用例草稿，可在右侧草稿池中查看。"
                : "当前测试点尚无用例草稿。";
        }
        if (instruction.contains("干啥") || instruction.contains("做什么") || instruction.contains("能做什么") || instruction.contains("你可以干啥")) {
            return "我可以回答测试设计相关问题，也可以为当前测试点生成用例草稿。直接告诉我你的需求即可。";
        }
        if (instruction.contains("修改") || instruction.contains("改一下") || instruction.contains("调整一下")) {
            return "草稿的修改请在右侧草稿池中手动编辑。";
        }
        if (instruction.contains("写代码") || instruction.contains("Python") || instruction.contains("脚本") || instruction.contains("闲聊")) {
            return "抱歉，我只能帮助处理测试用例相关的问题。";
        }
        // 兜底：无测试关键词→拒绝
        String[] testKeywords = {"测试","用例","覆盖","边界","等价类","异常","场景","步骤","预期","草稿","维度","设计"};
        boolean hasTestKeyword = false;
        for (String kw : testKeywords) {
            if (instruction.contains(kw)) { hasTestKeyword = true; break; }
        }
        if (!hasTestKeyword) {
            return "抱歉，我只能帮助处理当前测试点的用例设计相关问题。";
        }
        return null;
    }

    /** 只算当前测试点的草稿数（草稿池中 coverPoints 含 tpId 的），不含已入库用例 */
    private int getPointDraftCount(String recordNo, Integer projectId, String tpId) {
        com.alibaba.fastjson.JSONArray poolArr = loadPoolArr(recordNo, projectId);
        int count = 0;
        for (int i = 0; i < poolArr.size(); i++) {
            try {
                com.alibaba.fastjson.JSONObject d = poolArr.getJSONObject(i);
                if (d == null) continue;
                com.alibaba.fastjson.JSONArray cps = d.getJSONArray("coverPoints");
                if (cps != null && cps.contains(tpId)) count++;
            } catch (Exception ignored) {}
        }
        return count;
    }

    /** 全部点完成后：自动审查 → 收尾 */
    private void afterAllPointsDone(SkillRequest request, SseEmitter emitter, AiStreamBuffer buffer,
                                     AiGenerationRecord record, List<Map<String, Object>> testPoints) {
        String recordNo = record.getRecordNo();
        Integer projectId = request.getProjectId();
        // 收尾前检查 cancel：已取消的轮次标 STOPPED，绝不 finishRound(DONE)/finalizeV2
        if (handleCancelled(recordNo, projectId, buffer, emitter)) {
            return;
        }
        com.alibaba.fastjson.JSONArray poolArr = loadPoolArr(recordNo, projectId);
        boolean exhausted = testPoints.stream().anyMatch(t -> "FAILED".equals(String.valueOf(t.get("status"))));
        try {
            // 落库轮次 — BUG#9 修复：只记录本轮新增草稿（DONE 点的 caseDraftIds），不用整个 pool
            // "cancel 检查 + finishRound"按 recordNo 加锁原子化，与 stop 收尾互斥：
            // 已取消的轮次只能标 STOPPED，绝不写 DONE、绝不写草稿
            synchronized (recordLock(recordNo)) {
                if (handleCancelled(recordNo, projectId, buffer, emitter)) {
                    return;
                }
                List<CaseDraftDTO> newDrafts = new java.util.ArrayList<>();
                for (Map<String, Object> tp : testPoints) {
                    if ("DONE".equals(String.valueOf(tp.get("status")))) {
                        Object cdIds = tp.get("caseDraftIds");
                        if (cdIds instanceof List) {
                            for (Object idObj : (List<?>) cdIds) {
                                String draftId = String.valueOf(idObj);
                                // 从 pool 中按 draftId 查找
                                for (int i = 0; i < poolArr.size(); i++) {
                                    com.alibaba.fastjson.JSONObject d = poolArr.getJSONObject(i);
                                    if (d != null && draftId.equals(d.getString("draftId"))) {
                                        try {
                                            newDrafts.add(JSON.parseObject(d.toJSONString(), CaseDraftDTO.class));
                                        } catch (Exception ignored) {}
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                recordService.finishRound(recordNo, projectId,
                        JSON.toJSONString(newDrafts), null, null, JSON.toJSONString(request.getCitations()));
            }
            knowledgeService.settleCitations(request.getCitations());
            // 自动审查
            send(emitter, "stage", "{\"stage\":\"audit\"}");
            Map<String, Object> audit = coverageAuditor.audit(recordNo, projectId, request.getEntityId());
            send(emitter, "audit", JSON.toJSONString(audit));
            finalizeV2(emitter, request, record, buffer, testPoints, poolArr, audit, exhausted);
        } catch (Exception e) {
            // BUG#6 修复：审查失败时通知前端
            log.error("[V2] 自动审查失败: {}", e.getMessage());
            send(emitter, "error", "覆盖审查失败：" + e.getMessage());
            finalizeV2(emitter, request, record, buffer, testPoints, poolArr, null, true);
        }
    }

    /** V2 收尾：dimensions 从点状态派生，推送 result 事件 */
    private void finalizeV2(SseEmitter emitter, SkillRequest request, AiGenerationRecord record,
                             AiStreamBuffer buffer, List<Map<String, Object>> testPoints,
                             com.alibaba.fastjson.JSONArray poolArr, Map<String, Object> audit, boolean exhausted) {
        String recordNo = record.getRecordNo();
        Integer projectId = request.getProjectId();
        try {
            // BUG#1 修复：从 DB 加载 fresh stagePayload，不读陈旧 record 内存对象
            AiGenerationRecord freshRec = recordService.getByRecordNo(recordNo, projectId);
            com.alibaba.fastjson.JSONObject payload = freshRec != null && freshRec.getStagePayload() != null
                    ? JSON.parseObject(freshRec.getStagePayload()) : new com.alibaba.fastjson.JSONObject();
            payload.put("autoLoopExhausted", exhausted);
            // 用传入的最新 testPoints + cleanPool 覆写，确保 DB 数据与内存一致
            if (testPoints != null) payload.put("testPoints", JSON.toJSON(testPoints));
            recordService.updateStage(recordNo, projectId, "DONE", payload.toJSONString());
        } catch (Exception ignored) {}

        Map<String, Object> result = new HashMap<>();
        result.put("recordNo", recordNo);
        result.put("citations", request.getCitations());
        if (testPoints != null && !testPoints.isEmpty()) {
            result.put("dimensions", DimensionBoardBuilder.build(testPoints, poolArr, audit, exhausted));
            Map<String, Object> coverage = new HashMap<>();
            int covered = (int) testPoints.stream().filter(t -> "DONE".equals(String.valueOf(t.get("status")))).count();
            coverage.put("total", testPoints.size());
            coverage.put("covered", covered);
            java.util.List<String> uncoveredTitles = new java.util.ArrayList<>();
            for (Map<String, Object> tp : testPoints) {
                if (!"DONE".equals(String.valueOf(tp.get("status")))) {
                    uncoveredTitles.add(String.valueOf(tp.get("title")));
                }
            }
            coverage.put("uncovered", uncoveredTitles);
            result.put("coverage", coverage);
        }
        if (audit != null) result.put("audit", audit);
        // 安全网：过滤已采纳草稿，防止 removeAdoptedFromPool 失败时已入库草稿重现草稿池
        com.alibaba.fastjson.JSONArray cleanPool = new com.alibaba.fastjson.JSONArray();
        java.util.Set<String> adoptedIds = recordService.parseAdoptedDraftIds(recordNo);
        for (int i = 0; i < poolArr.size(); i++) {
            com.alibaba.fastjson.JSONObject d = poolArr.getJSONObject(i);
            String draftId = d != null ? d.getString("draftId") : null;
            if (draftId != null && adoptedIds.contains(draftId)) continue;
            cleanPool.add(d);
        }
        result.put("poolDrafts", cleanPool);
        String resultJson = JSON.toJSONString(result);
        buffer.finish(resultJson);
        send(emitter, "result", resultJson);
        safeComplete(emitter);
    }

    // ==================== V2 辅助方法 ====================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadTestPointsFromPayload(AiGenerationRecord record) {
        if (record.getStagePayload() == null) return null;
        try {
            com.alibaba.fastjson.JSONObject payload = JSON.parseObject(record.getStagePayload());
            com.alibaba.fastjson.JSONArray tpArr = payload.getJSONArray("testPoints");
            if (tpArr == null || tpArr.isEmpty()) return null;
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (int i = 0; i < tpArr.size(); i++) {
                result.add(new java.util.LinkedHashMap<>(tpArr.getJSONObject(i)));
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    /** 池更新锁：按 recordNo 隔离，防并发 2 线程池更新竞态 */
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> POOL_LOCKS = new java.util.concurrent.ConcurrentHashMap<>();

    private com.alibaba.fastjson.JSONArray loadPoolArr(String recordNo, Integer projectId) {
        AiGenerationRecord fresh = recordService.getByRecordNo(recordNo, projectId);
        if (fresh == null || fresh.getStagePayload() == null) return new com.alibaba.fastjson.JSONArray();
        try {
            com.alibaba.fastjson.JSONObject payload = JSON.parseObject(fresh.getStagePayload());
            com.alibaba.fastjson.JSONArray pool = payload.getJSONArray("pool");
            return pool != null ? pool : new com.alibaba.fastjson.JSONArray();
        } catch (Exception e) {
            return new com.alibaba.fastjson.JSONArray();
        }
    }

    /** 线程安全池更新：load(fresh from DB) + add + save，synchronized 按 recordNo 隔离 */
    private void addDraftsToPoolSync(String recordNo, Integer projectId,
                                     List<CaseDraftDTO> drafts, List<Map<String, Object>> allTestPoints) {
        Object lock = POOL_LOCKS.computeIfAbsent(recordNo, k -> new Object());
        synchronized (lock) {
            com.alibaba.fastjson.JSONArray poolArr = loadPoolArr(recordNo, projectId);
            for (CaseDraftDTO d : drafts) poolArr.add(JSON.toJSON(d));
            updatePointStatusInPayload(recordNo, projectId, allTestPoints, poolArr);
        }
    }

    private void persistTestPoints(String recordNo, Integer projectId, List<Map<String, Object>> testPoints) {
        try {
            com.alibaba.fastjson.JSONObject payload = new com.alibaba.fastjson.JSONObject();
            payload.put("testPoints", JSON.toJSON(testPoints));
            recordService.updateStage(recordNo, projectId, "CASE_GEN", payload.toJSONString());
        } catch (Exception e) {
            log.warn("持久化 testPoints 失败: {}", e.getMessage());
        }
    }

    private void updatePointStatusInPayload(String recordNo, Integer projectId,
                                            List<Map<String, Object>> testPoints,
                                            com.alibaba.fastjson.JSONArray poolArr) {
        try {
            AiGenerationRecord fresh = recordService.getByRecordNo(recordNo, projectId);
            com.alibaba.fastjson.JSONObject payload = fresh != null && fresh.getStagePayload() != null
                    ? JSON.parseObject(fresh.getStagePayload()) : new com.alibaba.fastjson.JSONObject();
            payload.put("testPoints", JSON.toJSON(testPoints));
            payload.put("pool", poolArr);
            // BUG#4 修复：逐点生成期间保持 CASE_GEN，只有 finalizeV2 最终设 DONE
            recordService.updateStage(recordNo, projectId, "CASE_GEN", payload.toJSONString());
        } catch (Exception e) {
            log.warn("更新点状态失败: {}", e.getMessage());
        }
    }

    private void pushDimensions(SseEmitter emitter, List<Map<String, Object>> testPoints,
                                 Map<String, Object> audit, boolean exhausted) {
        com.alibaba.fastjson.JSONArray poolArr = new com.alibaba.fastjson.JSONArray();
        send(emitter, "dimensions", JSON.toJSONString(
                DimensionBoardBuilder.build(testPoints, poolArr, audit, exhausted)));
    }

    /** 优先级排序：P0=0, P1=1, P2=2, 其他=3 */
    private static int priorityOrder(String p) {
        if ("P0".equals(p)) return 0;
        if ("P1".equals(p)) return 1;
        if ("P2".equals(p)) return 2;
        return 3;
    }

    private String buildDedupList(Integer projectId, Long entityId, AiGenerationRecord record) {
        String recordNo = record.getRecordNo();
        StringBuilder sb = new StringBuilder();
        List<TestCase> existingCases = testCaseService.list(
                new QueryWrapper<TestCase>().eq("project_id", projectId)
                        .eq("requirement_id", entityId).select("id", "case_name"));
        for (TestCase c : existingCases) {
            if (c.getCaseName() != null) sb.append("- ").append(c.getCaseName()).append('\n');
        }
        com.alibaba.fastjson.JSONArray poolArr = loadPoolArr(recordNo, projectId);
        for (int i = 0; i < poolArr.size(); i++) {
            try {
                com.alibaba.fastjson.JSONObject d = poolArr.getJSONObject(i);
                String name = d != null ? d.getString("caseName") : null;
                if (name != null) sb.append("- ").append(name).append('\n');
            } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    private String buildPerPointDedupList(Integer projectId, Long entityId, AiGenerationRecord record, String tpId) {
        StringBuilder sb = new StringBuilder();
        // 已入库用例（按需求查全部）
        List<TestCase> existingCases = testCaseService.list(
                new QueryWrapper<TestCase>().eq("project_id", projectId)
                        .eq("requirement_id", entityId).select("id", "case_name"));
        for (TestCase c : existingCases) {
            if (c.getCaseName() != null) sb.append("- ").append(c.getCaseName()).append('\n');
        }
        // 池内该点的草稿名（过滤已采纳的）
        java.util.Set<String> adoptedIds = recordService.parseAdoptedDraftIds(record.getRecordNo());
        com.alibaba.fastjson.JSONArray poolArr = loadPoolArr(record.getRecordNo(), projectId);
        for (int i = 0; i < poolArr.size(); i++) {
            try {
                com.alibaba.fastjson.JSONObject d = poolArr.getJSONObject(i);
                if (d == null) continue;
                String draftId = d.getString("draftId");
                if (draftId != null && adoptedIds.contains(draftId)) continue; // 跳过已采纳的
                com.alibaba.fastjson.JSONArray cps = d.getJSONArray("coverPoints");
                if (cps != null && cps.contains(tpId)) {
                    String name = d.getString("caseName");
                    if (name != null) sb.append("- ").append(name).append('\n');
                }
            } catch (Exception ignored) {}
        }
        return sb.toString();
    }

    /**
     * Layer 2 去重：新草稿与已有用例/池内草稿做 bigram 相似度检查。
     * ≥0.9 丢弃并计数，≥0.8 标 dupWithExisting（前端标黄）。
     * @return 丢弃的数量
     */
    @SuppressWarnings("unchecked")
    private int layer2Dedup(List<CaseDraftDTO> drafts, Integer projectId, Long entityId, String recordNo) {
        if (drafts.isEmpty()) return 0;
        // 收集已有名称 + 步骤文本
        java.util.List<String> existingNames = new java.util.ArrayList<>();
        java.util.List<String> existingTexts = new java.util.ArrayList<>();
        // 1. 已入库用例
        if (entityId != null) {
            List<TestCase> existingCases = testCaseService.list(
                    new QueryWrapper<TestCase>().eq("project_id", projectId)
                            .eq("requirement_id", entityId).select("id", "case_name", "test_steps"));
            for (TestCase c : existingCases) {
                if (c.getCaseName() != null) {
                    existingNames.add(c.getCaseName());
                    existingTexts.add(c.getCaseName() + (c.getTestSteps() != null ? c.getTestSteps().toString() : ""));
                }
            }
        }
        // 2. 草稿池中的草稿（追加生成时避免和已有草稿重复）
        java.util.Set<String> adoptedIds = recordService.parseAdoptedDraftIds(recordNo);
        com.alibaba.fastjson.JSONArray poolArr = loadPoolArr(recordNo, projectId);
        for (int i = 0; i < poolArr.size(); i++) {
            try {
                com.alibaba.fastjson.JSONObject d = poolArr.getJSONObject(i);
                if (d == null) continue;
                String draftId = d.getString("draftId");
                if (draftId != null && adoptedIds.contains(draftId)) continue; // 跳过已采纳的
                String name = d.getString("caseName");
                if (name != null) {
                    String steps = d.getJSONArray("testSteps") != null
                        ? d.getJSONArray("testSteps").toJSONString() : "";
                    existingNames.add(name);
                    existingTexts.add(name + steps);
                }
            } catch (Exception ignored) {}
        }
        int removed = 0;
        java.util.Iterator<CaseDraftDTO> iter = drafts.iterator();
        while (iter.hasNext()) {
            CaseDraftDTO d = iter.next();
            String draftText = (d.getCaseName() != null ? d.getCaseName() : "")
                    + (d.getTestSteps() != null ? d.getTestSteps().toString() : "");
            for (int i = 0; i < existingNames.size(); i++) {
                double sim = PointExtractor.bigramJaccard(draftText, existingTexts.get(i));
                if (sim >= 0.9) {
                    iter.remove();
                    removed++;
                    break;
                }
                if (sim >= 0.8) {
                    d.setDupWithExisting(true);
                }
            }
        }
        return removed;
    }

    /** 从 stagePayload.testPoints 查维度标题（单维度重生拼指令用） */
    private String findTestPointTitle(AiGenerationRecord record, String tpId) {
        com.alibaba.fastjson.JSONObject tp = findTestPoint(record, tpId);
        if (tp != null) {
            return tp.getString("title") != null ? tp.getString("title") : tpId;
        }
        return tpId;
    }

    /** 从 stagePayload.testPoints 查维度分类 */
    private String findTestPointCategory(AiGenerationRecord record, String tpId) {
        com.alibaba.fastjson.JSONObject tp = findTestPoint(record, tpId);
        if (tp != null) {
            String cat = tp.getString("category");
            return cat != null ? cat : "FUNCTION";
        }
        return "FUNCTION";
    }

    /** 从 stagePayload.testPoints 中按 ID 查找维度点 JSON 对象 */
    private com.alibaba.fastjson.JSONObject findTestPoint(AiGenerationRecord record, String tpId) {
        if (record.getStagePayload() != null) {
            try {
                com.alibaba.fastjson.JSONArray tpArr = JSON.parseObject(record.getStagePayload()).getJSONArray("testPoints");
                if (tpArr != null) {
                    for (int i = 0; i < tpArr.size(); i++) {
                        com.alibaba.fastjson.JSONObject tp = tpArr.getJSONObject(i);
                        if (tp != null && tpId.equals(tp.getString("id"))) {
                            return tp;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 解析 @OutputGuardrails 已验证的 JSON 为 DraftGenResult<CaseDraftDTO>。
     * 替代旧 CaseDraftParser 的 168 行手工 JSON 提取 + 括号平衡 + 自动补全逻辑。
     */
    private static final java.util.Set<String> VALID_TYPES = new java.util.HashSet<>(
            java.util.Arrays.asList("FUNCTION", "API", "PERFORMANCE", "COMPATIBILITY", "SMOKE"));
    private static final java.util.Set<String> VALID_PRIORITIES = new java.util.HashSet<>(
            java.util.Arrays.asList("P0", "P1", "P2"));

    private DraftGenResult<CaseDraftDTO> parseCaseGenResult(String full) {
        if (full == null || full.isBlank()) {
            throw new BusinessException("模型未返回正文内容");
        }
        com.alibaba.fastjson.JSONObject root = JSON.parseObject(full);
        if (root == null) {
            throw new BusinessException("模型输出不是合法 JSON 对象");
        }
        com.alibaba.fastjson.JSONArray testCases = root.getJSONArray("test_cases");
        if (testCases == null || testCases.isEmpty()) {
            throw new BusinessException("模型输出缺少 test_cases 数组");
        }
        DraftGenResult<CaseDraftDTO> result = new DraftGenResult<>();
        for (int i = 0; i < testCases.size(); i++) {
            com.alibaba.fastjson.JSONObject obj = testCases.getJSONObject(i);
            if (obj == null) continue;
            CaseDraftDTO draft = mapToDraft(obj);
            if (draft.getCaseName() != null && !draft.getCaseName().trim().isEmpty()) {
                result.getDrafts().add(draft);
            }
        }
        if (result.getDrafts().isEmpty()) {
            throw new BusinessException("模型输出中没有有效用例条目");
        }
        return result;
    }

    /**
     * JSON 对象 → CaseDraftDTO（保留枚举兜底逻辑）
     */
    private CaseDraftDTO mapToDraft(com.alibaba.fastjson.JSONObject obj) {
        CaseDraftDTO draft = new CaseDraftDTO();
        boolean fallback = false;
        draft.setCaseName(obj.getString("caseName"));
        draft.setPreCondition(obj.getString("preCondition"));
        draft.setTags(obj.getString("tags"));
        draft.setExpectDuration(obj.getInteger("expectDuration"));
        String caseType = normalizeEnum(obj.getString("caseType"));
        if (caseType == null || !VALID_TYPES.contains(caseType)) {
            if (caseType != null) fallback = true;
            caseType = "FUNCTION";
        }
        draft.setCaseType(caseType);
        String priority = normalizeEnum(obj.getString("priority"));
        if (priority == null || !VALID_PRIORITIES.contains(priority)) {
            if (priority != null) fallback = true;
            priority = "P2";
        }
        draft.setPriority(priority);
        com.alibaba.fastjson.JSONArray steps = obj.getJSONArray("testSteps");
        if (steps != null) {
            List<CaseDraftDTO.StepItem> stepItems = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                com.alibaba.fastjson.JSONObject s = steps.getJSONObject(i);
                if (s == null || s.getString("step") == null) continue;
                CaseDraftDTO.StepItem item = new CaseDraftDTO.StepItem();
                item.setStep(s.getString("step"));
                item.setExpected(s.getString("expected"));
                stepItems.add(item);
            }
            draft.setTestSteps(stepItems);
        }
        draft.setDesignMethod(normalizeEnum(obj.getString("designMethod")));
        com.alibaba.fastjson.JSONArray coverPoints = obj.getJSONArray("coverPoints");
        if (coverPoints != null) {
            List<String> cpList = new ArrayList<>();
            for (int i = 0; i < coverPoints.size(); i++) {
                String cp = coverPoints.getString(i);
                if (cp != null && !cp.trim().isEmpty()) cpList.add(cp.trim());
            }
            draft.setCoverPoints(cpList);
        }
        draft.setEnumFallback(fallback);
        return draft;
    }

    private static String normalizeEnum(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim().toUpperCase();
    }

    // ==================== TokenStream 通用桥接 ====================

    /**
     * TokenStream → SSE + AiStreamBuffer 回调编排（替代旧 streamChat + CountDownLatch 阻塞模式）。
     * cancel：buffer.isCancelRequested() 时忽略后续 chunk，onComplete 走 stopped 分支。
     */
    private void runStream(TokenStream ts, AiStreamBuffer buffer, SseEmitter emitter,
                           String recordNo, Integer projectId,
                           java.util.List<Citation> citationsCollector,
                           java.util.function.Consumer<String> onComplete) {
        runStream(ts, buffer, emitter, recordNo, projectId, citationsCollector, onComplete, null);
    }

    private void runStream(TokenStream ts, AiStreamBuffer buffer, SseEmitter emitter,
                           String recordNo, Integer projectId,
                           java.util.List<Citation> citationsCollector,
                           java.util.function.Consumer<String> onComplete,
                           CaseStreamParser caseParser) {
        runStream(ts, buffer, emitter, recordNo, projectId, citationsCollector, onComplete, caseParser, 0);
    }

    /**
     * @param baseOffset 本轮开始时 buffer 已有长度（自动补轮复用同一 buffer，
     *                   本轮全文 = buffer.text().substring(baseOffset)，避免混入前轮内容）
     */
    private void runStream(TokenStream ts, AiStreamBuffer buffer, SseEmitter emitter,
                           String recordNo, Integer projectId,
                           java.util.List<Citation> citationsCollector,
                           java.util.function.Consumer<String> onComplete,
                           CaseStreamParser caseParser, int baseOffset) {
        final long streamStart = System.currentTimeMillis();
        final long[] lastFlush = {System.currentTimeMillis()};
        final int[] thinkingChars = {0};
        final boolean[] firstTokenLogged = {false};
        final boolean[] retrievalLogged = {false};
        // 心跳：流式 + extractCases 提取期间保持 SSE 连接活跃
        java.util.concurrent.ScheduledFuture<?> hb = HEARTBEAT.scheduleAtFixedRate(
                () -> send(emitter, "ping", "{}"), 15, 15, java.util.concurrent.TimeUnit.SECONDS);
        // 绑定"尽力中断"取消句柄：langchain4j 1.19 的 TokenStream/AiServices 不暴露底层
        // okhttp Call，框架层拿不到可真正断开 HTTP 的句柄，服务端生成仍会继续（token 照常计费）。
        // 这里能做的是立即收尾：停心跳、轮次标 STOPPED、通知前端并关闭 SSE，
        // 用户无需等模型跑完才看到"已停止"；后续 chunk 由 onPartialResponse 的 cancel 检查丢弃。
        buffer.bindCancel(() -> {
            hb.cancel(false);
            // 与工作线程的"检查+写入"按 recordNo 互斥，防止 stopRound 之后又被 finishRound 写 DONE
            synchronized (recordLock(recordNo)) {
                try {
                    recordService.stopRound(recordNo, projectId, buffer.text());
                    recordService.updateStage(recordNo, projectId, "STOPPED", null);
                } catch (Exception e) {
                    log.warn("[停止] 流式取消收尾写库失败 | recordNo={} err={}", recordNo, e.getMessage());
                }
            }
            buffer.markStopped();
            send(emitter, "stopped", "已停止");
            safeComplete(emitter);
        });
        ts.onPartialThinking(thinking -> {
                    if (thinking != null && thinking.text() != null) {
                        thinkingChars[0] += thinking.text().length();
                        send(emitter, "thinking", thinking.text());
                    }
                })
                .onPartialResponse(chunk -> {
                    if (!firstTokenLogged[0]) {
                        firstTokenLogged[0] = true;
                        log.info("[性能] 阶段6-LLM 首 token 到达 | recordNo={} 距流开始={}ms", recordNo, System.currentTimeMillis() - streamStart);
                    }
                    if (buffer.isCancelRequested() || chunk == null || chunk.isEmpty()) return;
                    buffer.append(chunk);
                    send(emitter, "delta", chunk);
                    if (caseParser != null) {
                        caseParser.feed(chunk);
                        for (CaseDraftDTO draft : caseParser.drainNew()) {
                            send(emitter, "case", JSON.toJSONString(draft));
                        }
                    }
                    long now = System.currentTimeMillis();
                    if (now - lastFlush[0] > 2000) {
                        lastFlush[0] = now;
                        recordService.updateRoundRawText(recordNo, projectId, buffer.text());
                    }
                })
                .onRetrieved(contents -> {
                    if (!retrievalLogged[0]) {
                        retrievalLogged[0] = true;
                        log.info("[性能] 阶段5-RAG 检索完成 | recordNo={} 距流开始={}ms 命中文档数={}",
                                recordNo, System.currentTimeMillis() - streamStart, contents.size());
                    }
                    for (Content c : contents) {
                        try {
                            String docIdStr = c.textSegment().metadata().getString("docId");
                            String docTitle = c.textSegment().metadata().getString("docTitle");
                            String preview = abbreviate(c.textSegment().text(), 80);
                            log.info("[性能] RAG 命中文档 | docId={} docTitle={} 预览={}",
                                    docIdStr, docTitle, preview);
                            if (docIdStr != null && citationsCollector != null) {
                                citationsCollector.add(new Citation(
                                        Long.valueOf(docIdStr), docTitle,
                                        abbreviate(c.textSegment().text(), 100)));
                            }
                        } catch (Exception ignore) {
                            // metadata 缺失或格式异常，跳过
                        }
                    }
                })
                .onCompleteResponse(response -> {
                    hb.cancel(false);
                    String bufferedAll = buffer.text();
                    String full = bufferedAll != null && bufferedAll.length() > baseOffset
                            ? bufferedAll.substring(baseOffset) : "";
                    // OutputGuardrails + TokenStream 模式下 onPartialResponse 可能被框架缓冲，
                    // 导致 buffer 为空；从 ChatResponse.aiMessage().text() 取全文作为 fallback
                    if (full.isEmpty() && response != null && response.aiMessage() != null) {
                        full = response.aiMessage().text();
                        if (full != null && !full.isEmpty()) {
                            buffer.append(full);
                            log.info("[性能] buffer 为空，从 ChatResponse 恢复全文 | 字符={}", full.length());
                        }
                    }
                    long totalMs = System.currentTimeMillis() - streamStart;
                    log.info("[性能] 阶段7-LLM 生成完成 | recordNo={} 总流式耗时={}ms 正文字符={} 思考字符={}",
                            recordNo, totalMs, full.length(), thinkingChars[0]);
                    if (response != null && response.metadata() != null) {
                        var md = response.metadata();
                        var usage = md.tokenUsage();
                        log.info("[AI诊断] 流式完成 | recordNo={} finishReason={} 正文字符={} 思考字符={} token(in/out)={}/{}",
                                recordNo, md.finishReason(), full.length(), thinkingChars[0],
                                usage != null ? usage.inputTokenCount() : -1,
                                usage != null ? usage.outputTokenCount() : -1);
                    }
                    if (buffer.isCancelRequested()) {
                        // 解析半成品中的已闭合用例，存入轮次 drafts（供续跑使用，幂等）
                        CaseStreamParser partialParser = new CaseStreamParser();
                        partialParser.feed(full);
                        DraftGenResult<CaseDraftDTO> partial = partialParser.finish();
                        if (!partial.getDrafts().isEmpty()) {
                            assignDraftIds(partial.getDrafts());
                            recordService.updateRoundDrafts(recordNo, projectId, JSON.toJSONString(partial.getDrafts()));
                        }
                        // 统一取消收尾：轮次标 STOPPED（cancel handle 已收尾时幂等跳过），绝不写 DONE
                        handleCancelled(recordNo, projectId, buffer, emitter);
                    } else {
                        onComplete.accept(full);
                    }
                })
                .onError(error -> {
                    hb.cancel(false);
                    String allText = buffer.text();
                    String roundText = allText != null && allText.length() > baseOffset
                            ? allText.substring(baseOffset) : "";
                    if (buffer.isCancelRequested()) {
                        // 解析半成品中的已闭合用例，存入轮次 drafts（供续跑使用，幂等）
                        CaseStreamParser partialParser = new CaseStreamParser();
                        partialParser.feed(roundText);
                        DraftGenResult<CaseDraftDTO> partial = partialParser.finish();
                        if (!partial.getDrafts().isEmpty()) {
                            assignDraftIds(partial.getDrafts());
                            recordService.updateRoundDrafts(recordNo, projectId, JSON.toJSONString(partial.getDrafts()));
                        }
                        // 统一取消收尾（cancel handle 已收尾时幂等跳过），绝不写 DONE
                        handleCancelled(recordNo, projectId, buffer, emitter);
                    } else {
                        recordService.failRound(recordNo, projectId, error.getMessage(), roundText);
                        recordService.updateStage(recordNo, projectId, "FAILED", null);
                        buffer.fail(error.getMessage());
                        send(emitter, "error", error.getMessage() != null ? error.getMessage() : "生成失败");
                    }
                    emitter.complete();
                })
                .start();
    }

    // ==================== 上下文组装 ====================

    /**
     * 上下文组装（优化版）：
     * - L1 查一次 Requirement，复用到 L2/L3/L5（消除 3 次冗余查询）
     * - L2/L3/L4/L5 并行加载（CompletableFuture）
     * - 每阶段计时日志
     */
    private String buildContextText(SkillRequest request, List<String> trimmedNotes) {
        Long eid = request.getEntityId();
        Integer pid = request.getProjectId();
        long t0 = System.currentTimeMillis();

        // L1 核心需求：查一次，失败直接抛（失去锚点无法生成）
        Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", eid).eq("project_id", pid));
        if (req == null) {
            throw new BusinessException("需求不存在");
        }
        ContextBlocks.Block l1 = requirementCoreSource.load(req);
        long t1 = System.currentTimeMillis();
        log.info("[性能] L1 核心需求查询 | reqId={} 耗时={}ms", eid, t1 - t0);

        // L2/L3/L4/L5 并行加载（复用 L1 的 Requirement 对象）
        java.util.concurrent.CompletableFuture<ContextBlocks.Block> l2F =
                java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        loadSafely(() -> relatedRequirementSource.load(req, pid), "L2"));
        java.util.concurrent.CompletableFuture<ContextBlocks.Block> l3F =
                java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        loadSafely(() -> moduleRequirementSource.load(req, pid), "L3"));
        java.util.concurrent.CompletableFuture<ContextBlocks.Block> l4F =
                java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        loadSafely(() -> existingCaseSource.load(eid, pid), "L4"));
        java.util.concurrent.CompletableFuture<ContextBlocks.Block> l5F =
                java.util.concurrent.CompletableFuture.supplyAsync(() ->
                        loadSafely(() -> historyBugSource.load(req, pid), "L5"));

        java.util.concurrent.CompletableFuture.allOf(l2F, l3F, l4F, l5F).join();
        long t2 = System.currentTimeMillis();
        log.info("[性能] L2-L5 并行加载完成 | reqId={} 耗耗={}ms (L2={}, L3={}, L4={}, L5={})",
                eid, t2 - t1, l2F.isDone(), l3F.isDone(), l4F.isDone(), l5F.isDone());

        List<ContextBlocks.Block> blocks = new ArrayList<>();
        if (l1 != null) blocks.add(l1);
        addIfNotNull(blocks, l2F.join());
        addIfNotNull(blocks, l3F.join());
        addIfNotNull(blocks, l4F.join());
        addIfNotNull(blocks, l5F.join());

        String result = ContextBlocks.assemble(blocks, trimmedNotes);
        long t3 = System.currentTimeMillis();
        log.info("[性能] 上下文组装完成 | reqId={} 总耗时={}ms 上下文字符={} 预估token={}",
                eid, t3 - t0, result.length(), com.mokatest.platform.demos.ai.context.ContextBlocks.estimateTokens(result));
        return result;
    }

    private void addIfNotNull(List<ContextBlocks.Block> blocks, ContextBlocks.Block b) {
        if (b != null) blocks.add(b);
    }

    private ContextBlocks.Block loadSafely(java.util.function.Supplier<ContextBlocks.Block> loader, String level) {
        try {
            return loader.get();
        } catch (Exception e) {
            log.warn("上下文来源 {} 加载失败，已降级跳过: {}", level, e.getMessage());
            return null;
        }
    }

    // ==================== 记录管理 ====================

    private AiGenerationRecord createRecord(SkillRequest request, String scene, List<String> trimmedNotes) {
        AiGenerationRecord record = recordService.create(
                request.getProjectId(), request.getTeamId(), request.getUserId(),
                scene, request.getEntityId(), buildSummary(request, trimmedNotes));
        recordService.appendSnapshot(record.getRecordNo(), request.getProjectId(), null);
        // §10.2-2：覆盖基线继承——新会话复用归档会话的 test_points
        inheritCoverageBaseline(record, request.getProjectId(), request.getEntityId());
        return record;
    }

    private String buildSummary(SkillRequest request, List<String> trimmedNotes) {
        StringBuilder sb = new StringBuilder();
        sb.append("entityId=").append(request.getEntityId());
        if (request.getInstruction() != null && !request.getInstruction().isEmpty()) {
            sb.append("；指令：").append(request.getInstruction().length() > 100
                    ? request.getInstruction().substring(0, 100) : request.getInstruction());
        }
        if (request.getCitations() != null && !request.getCitations().isEmpty()) {
            sb.append("；知识库：");
            request.getCitations().stream().map(Citation::getTitle).distinct().limit(5)
                    .forEach(t -> sb.append("《").append(t).append("》"));
        }
        if (trimmedNotes != null && !trimmedNotes.isEmpty()) {
            sb.append("；裁剪：").append(String.join(" | ", trimmedNotes));
        }
        return sb.length() > 480 ? sb.substring(0, 480) : sb.toString();
    }

    public void stop(String recordNo, String tpId) {
        Integer projectId = ProjectContextHolder.getProjectId();
        AiGenerationRecord record = recordService.requireValid(recordNo, projectId);
        // 逐点对话 buffer key 为 recordNo_tpId（见 casesStream bufferKey），tpId 非空时先精确匹配
        AiStreamBuffer buffer = null;
        if (tpId != null && !tpId.isBlank()) {
            buffer = bufferRegistry.get(record.getRecordNo() + "_" + tpId);
        }
        if (buffer == null) {
            buffer = bufferRegistry.get(record.getRecordNo());
        }
        if (buffer != null && buffer.getState() == AiStreamBuffer.State.STREAMING) {
            buffer.requestCancel();
        } else {
            // 兜底：buffer 不存在或已结束，直接把 DB 轮次标 STOPPED（无 GENERATING 轮次时为 no-op）
            recordService.stopRound(record.getRecordNo(), projectId, null);
        }
    }

    /**
     * 已取消时的统一收尾：轮次标 STOPPED，绝不写 DONE、绝不写草稿。
     * cancel handle 已提前收尾（state=STOPPED）时只保证 emitter 关闭，不重复写库。
     *
     * @return true 表示已取消，调用方应立即 return，不得再执行任何落库/收尾写入
     */
    private boolean handleCancelled(String recordNo, Integer projectId, AiStreamBuffer buffer, SseEmitter emitter) {
        if (!buffer.isCancelRequested()) {
            return false;
        }
        // 与 cancel handle / 写草稿池共用按 recordNo 的锁：
        // stop 的收尾写库与工作线程的"检查+finishRound"互斥，杜绝 stopRound 后又冒出 DONE 轮次
        synchronized (recordLock(recordNo)) {
            if (buffer.getState() != AiStreamBuffer.State.STOPPED) {
                try {
                    recordService.stopRound(recordNo, projectId, null);
                    // stage 标 STOPPED 而非 DONE：取消的会话不是"完成"，
                    // 前端入口卡片对未知 stage 按"已结束、可查看进度"处理
                    recordService.updateStage(recordNo, projectId, "STOPPED", null);
                } catch (Exception e) {
                    log.warn("[停止] 取消收尾写库失败 | recordNo={} err={}", recordNo, e.getMessage());
                }
                buffer.markStopped();
                send(emitter, "stopped", "已停止");
            }
        }
        // cancel handle 的尽力中断可能给本线程留了 interrupt 标记，清掉以免污染线程池复用
        Thread.interrupted();
        safeComplete(emitter);
        return true;
    }

    /** 按 recordNo 的互斥锁（与 POOL_LOCKS 同实例）：串行化 cancel 收尾与草稿池/轮次写入 */
    private Object recordLock(String recordNo) {
        return POOL_LOCKS.computeIfAbsent(recordNo, k -> new Object());
    }

    public void regenerateStream(SkillRequest baseRequest, SseEmitter emitter) {
        String recordNo = baseRequest.getRecordNo();
        AiGenerationRecord record = recordService.requireValid(recordNo, baseRequest.getProjectId());
        java.util.List<java.util.Map<String, Object>> rounds = recordService.parseRounds(record.getOutputSnapshot());
        if (rounds.isEmpty()) {
            throw new BusinessException("没有可重新生成的轮次");
        }
        java.util.Map<String, Object> lastRound = rounds.get(rounds.size() - 1);
        String lastStatus = lastRound == null ? null : String.valueOf(lastRound.get("status"));
        if (!"FAILED".equals(lastStatus) && !"DONE".equals(lastStatus)) {
            throw new BusinessException("仅允许对已完成或失败的轮次重新生成");
        }
        String instruction = lastRound == null ? null : String.valueOf(lastRound.get("instruction"));
        recordService.removeLastRound(recordNo, baseRequest.getProjectId());
        baseRequest.setInstruction(instruction);
        generateStream(baseRequest, true, emitter);
    }

    public void deleteRecord(String recordNo) {
        recordService.delete(recordNo, ProjectContextHolder.getProjectId());
    }

    /** 归档会话记录（标记为 ARCHIVED，不影响已入库用例） */
    public void archiveRecord(String recordNo) {
        Integer projectId = ProjectContextHolder.getProjectId();
        recordService.archive(recordNo, projectId);
    }


    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> adopt(String recordNo, List<CaseDraftDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("请至少选择一条用例入库");
        }
        Integer projectId = ProjectContextHolder.getProjectId();
        AiGenerationRecord record = recordService.requireValid(recordNo, projectId);
        Long requirementId = record.getEntityId();
        Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", requirementId).eq("project_id", projectId).select("id", "module_id"));
        if (req == null) {
            throw new BusinessException("锚定需求不存在或已被删除");
        }

        java.util.Set<String> adoptedIds = recordService.parseAdoptedDraftIds(recordNo);
        List<CaseDraftDTO> toSave = new ArrayList<>();
        int skipped = 0;
        for (CaseDraftDTO draft : items) {
            if (draft.getCaseName() == null || draft.getCaseName().trim().isEmpty()) continue;
            if (draft.getDraftId() != null && adoptedIds.contains(draft.getDraftId())) {
                skipped++;
                continue;
            }
            toSave.add(draft);
        }

        List<String> duplicateNames = new ArrayList<>();
        if (!toSave.isEmpty()) {
            List<TestCase> existing = testCaseService.list(
                    new QueryWrapper<TestCase>().eq("project_id", projectId)
                            .eq("requirement_id", requirementId).select("id", "case_name"));
            java.util.Set<String> existingNames = new java.util.HashSet<>();
            for (TestCase c : existing) {
                if (c.getCaseName() != null) existingNames.add(c.getCaseName().trim());
            }
            java.util.Set<String> batchNames = new java.util.HashSet<>();
            toSave.removeIf(draft -> {
                String name = draft.getCaseName().trim();
                if (existingNames.contains(name) || !batchNames.add(name)) {
                    duplicateNames.add(name);
                    return true;
                }
                return false;
            });
            skipped += duplicateNames.size();
        }

        List<Integer> savedIds = new ArrayList<>();
        List<String> adoptedDraftIds = new ArrayList<>();
        java.util.List<java.util.Map<String, Object>> entries = new java.util.ArrayList<>();
        com.alibaba.fastjson.JSONArray savedList = new com.alibaba.fastjson.JSONArray();
        for (CaseDraftDTO draft : toSave) {
            TestCase testCase = caseDraftMapper.toEntity(draft, projectId,
                    requirementId.intValue(), req.getModuleId());
            cn.dev33.satoken.util.SaResult saveResult = testCaseService.saveOrUpdateCase(testCase);
            if (saveResult.getCode() != 200) {
                throw new BusinessException("用例入库失败：" + saveResult.getMsg());
            }
            Integer caseId = (Integer) saveResult.getData();
            savedIds.add(caseId);
            java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("draftId", draft.getDraftId());
            entry.put("caseId", caseId);
            entries.add(entry);
            com.alibaba.fastjson.JSONObject saved = new com.alibaba.fastjson.JSONObject();
            saved.put("draftId", draft.getDraftId());
            saved.put("caseId", caseId);
            saved.put("caseCode", "CASE-" + caseId);
            savedList.add(saved);
            if (draft.getDraftId() != null) adoptedDraftIds.add(draft.getDraftId());
        }
        if (savedIds.isEmpty() && skipped > 0) {
            throw new BusinessException("所选条目均已入库过，无需重复入库");
        }
        if (savedIds.isEmpty()) {
            throw new BusinessException("所选条目均无效（用例名称为空）");
        }
        recordService.mergeAdoption(recordNo, projectId, entries);

        // 问题1修复：采纳成功后从 stage_payload.pool 移除已采纳草稿（pool 是唯一事实源）
        removeAdoptedFromPool(recordNo, projectId, adoptedDraftIds);

        Map<String, Object> result = new HashMap<>();
        result.put("savedIds", savedIds);
        result.put("savedList", savedList);
        result.put("adoptedDraftIds", adoptedDraftIds);
        result.put("skippedCount", skipped);
        result.put("duplicateNames", duplicateNames);
        return result;
    }

    private void assignDraftIds(List<CaseDraftDTO> drafts) {
        for (CaseDraftDTO draft : drafts) {
            if (draft.getDraftId() == null || draft.getDraftId().isEmpty()) {
                draft.setDraftId(java.util.UUID.randomUUID().toString().replace("-", ""));
            }
        }
    }

    public List<AiGenerationRecord> listRecords(Long requirementId) {
        Integer projectId = ProjectContextHolder.getProjectId();
        return recordService.listByEntity(projectId, CaseGenPrompts.SCENE_CASE, requirementId);
    }

    /**
     * §9.1 覆盖度联动：入库用例被删时，将该需求下 AI 会话的审查结论标记 stale。
     * 前端收到 stale 标记后显示「有用例变更，建议重新审查」。
     */
    public void markAuditStale(Long requirementId, Integer projectId) {
        if (requirementId == null || projectId == null) return;
        List<AiGenerationRecord> records = recordService.listByEntity(projectId, CaseGenPrompts.SCENE_CASE, requirementId);
        for (AiGenerationRecord record : records) {
            if (record.getStagePayload() == null) continue;
            try {
                com.alibaba.fastjson.JSONObject payload = JSON.parseObject(record.getStagePayload());
                String auditJson = payload.getString("audit");
                if (auditJson != null) {
                    com.alibaba.fastjson.JSONObject audit = JSON.parseObject(auditJson);
                    audit.put("stale", true);
                    payload.put("audit", audit.toJSONString());
                    recordService.updateStage(record.getRecordNo(), projectId, record.getCurrentStage(), payload.toJSONString());
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * §10.2-1：检查该需求是否存在已归档且审查通过的会话。
     * 返回 {archivedAudit: {overallVerdict, overallSummary, time, stale}} 或空 Map。
     */
    public Map<String, Object> getArchivedAuditSummary(Long requirementId, Integer projectId) {
        Map<String, Object> empty = new java.util.LinkedHashMap<>();
        if (requirementId == null || projectId == null) return empty;
        List<AiGenerationRecord> records = recordService.listByEntity(projectId, CaseGenPrompts.SCENE_CASE, requirementId);
        for (AiGenerationRecord record : records) {
            if (!"ARCHIVED".equals(record.getCurrentStage())) continue;
            if (record.getStagePayload() == null) continue;
            try {
                com.alibaba.fastjson.JSONObject payload = JSON.parseObject(record.getStagePayload());
                String auditJson = payload.getString("audit");
                if (auditJson != null) {
                    com.alibaba.fastjson.JSONObject audit = JSON.parseObject(auditJson);
                    Map<String, Object> summary = new java.util.LinkedHashMap<>();
                    summary.put("overallVerdict", audit.getString("overallVerdict"));
                    summary.put("overallSummary", audit.getString("overallSummary"));
                    summary.put("stale", audit.getBooleanValue("stale"));
                    Map<String, Object> result = new java.util.LinkedHashMap<>();
                    result.put("archivedAudit", summary);
                    return result;
                }
            } catch (Exception ignored) {}
        }
        return empty;
    }

    /**
     * §10.2-2：覆盖基线继承——新会话复用归档会话的 test_points。
     * 需求内容未变更时，维度点清单应保持一致，避免重新生成时维度漂移。
     */
    private void inheritCoverageBaseline(AiGenerationRecord newRecord, Integer projectId, Long entityId) {
        if (entityId == null) return;
        List<AiGenerationRecord> records = recordService.listByEntity(projectId, CaseGenPrompts.SCENE_CASE, entityId);
        for (AiGenerationRecord oldRecord : records) {
            if (oldRecord.getRecordNo().equals(newRecord.getRecordNo())) continue;
            if (!"ARCHIVED".equals(oldRecord.getCurrentStage())) continue;
            if (oldRecord.getStagePayload() == null) continue;
            try {
                com.alibaba.fastjson.JSONObject oldPayload = JSON.parseObject(oldRecord.getStagePayload());
                com.alibaba.fastjson.JSONArray testPoints = oldPayload.getJSONArray("testPoints");
                if (testPoints != null && !testPoints.isEmpty()) {
                    com.alibaba.fastjson.JSONObject newPayload = new com.alibaba.fastjson.JSONObject();
                    newPayload.put("testPoints", testPoints);
                    // 初始 covered 为空集，uncovered = 全部 testPoints
                    com.alibaba.fastjson.JSONArray uncovered = new com.alibaba.fastjson.JSONArray();
                    for (int i = 0; i < testPoints.size(); i++) {
                        uncovered.add(testPoints.get(i));
                    }
                    newPayload.put("covered", new com.alibaba.fastjson.JSONArray());
                    newPayload.put("uncovered", uncovered);
                    recordService.updateStage(newRecord.getRecordNo(), projectId, newRecord.getCurrentStage(), newPayload.toJSONString());
                    log.info("[覆盖基线] 继承归档会话 test_points | newRecord={} oldRecord={} tpCount={}",
                            newRecord.getRecordNo(), oldRecord.getRecordNo(), testPoints.size());
                    return;
                }
            } catch (Exception ignored) {}
        }
    }
    public Map<String, Object> auditCoverage(String recordNo, Integer projectId) {
        AiGenerationRecord record = recordService.getByRecordNo(recordNo, projectId);
        if (record == null) {
            throw new BusinessException("生成记录不存在");
        }
        Long entityId = record.getEntityId();
        return coverageAuditor.audit(recordNo, projectId, entityId);
    }

    private String latestRoundDrafts(String outputSnapshot) {
        java.util.List<java.util.Map<String, Object>> rounds = recordService.parseRounds(outputSnapshot);
        if (rounds.isEmpty()) return null;
        java.util.Map<String, Object> latest = rounds.get(rounds.size() - 1);
        if (latest == null) return null;
        Object drafts = latest.get("drafts");
        return drafts == null ? null : JSON.toJSONString(drafts);
    }

    /** 加载中断轮（STOPPED）的结构化 drafts，供续跑合并 */
    private List<CaseDraftDTO> loadStoppedRoundDrafts(java.util.List<java.util.Map<String, Object>> rounds) {
        if (rounds == null || rounds.isEmpty()) return null;
        java.util.Map<String, Object> lastRound = rounds.get(rounds.size() - 1);
        if (!"STOPPED".equals(String.valueOf(lastRound.get("status")))) return null;
        Object draftsObj = lastRound.get("drafts");
        if (draftsObj == null) return null;
        return JSON.parseArray(JSON.toJSONString(draftsObj), CaseDraftDTO.class);
    }

    public SkillRequest buildRequest(Long requirementId, String recordNo, String instruction) {
        Integer projectId = ProjectContextHolder.getProjectId();
        if (projectId == null) {
            throw new BusinessException("缺少项目上下文（X-Project-Id）");
        }
        // 校验需求归属当前项目，防止跨项目访问
        if (requirementId != null && requirementId > 0) {
            Requirement req = requirementMapper.selectById(requirementId);
            if (req == null || !projectId.equals(req.getProjectId())) {
                throw new BusinessException("需求不存在或不属于当前项目");
            }
        }
        SkillRequest request = new SkillRequest();
        request.setUserId(StpUtil.getLoginIdAsString());
        request.setTeamId(TeamContextHolder.getTeamId());
        request.setProjectId(projectId);
        request.setEntityId(requirementId);
        request.setRecordNo(recordNo);
        request.setInstruction(instruction);
        return request;
    }

    /**
     * §4：saveDrafts 写 stage_payload.pool（单一事实源），不再改 rounds。
     * rounds 是只读历史档案，修改只在 pool 上。
     */
    public void saveDrafts(String recordNo, Integer projectId, String draftsJson) {
        AiGenerationRecord record = recordService.getByRecordNo(recordNo, projectId);
        if (record == null) throw new BusinessException("生成记录不存在");
        try {
            com.alibaba.fastjson.JSONObject payload = record.getStagePayload() != null
                    ? JSON.parseObject(record.getStagePayload()) : new com.alibaba.fastjson.JSONObject();
            payload.put("pool", JSON.parse(draftsJson));
            recordService.updateStage(recordNo, projectId, record.getCurrentStage(), payload.toJSONString());
        } catch (Exception e) {
            throw new BusinessException("草稿保存失败: " + e.getMessage());
        }
    }

    /**
     * 问题1修复：采纳成功后从 stage_payload.pool 移除已采纳草稿。
     * pool 是唯一事实源，采纳即出池必须在后端完成，否则刷新后已采纳草稿复活。
     */
    private void removeAdoptedFromPool(String recordNo, Integer projectId, List<String> adoptedDraftIds) {
        if (adoptedDraftIds == null || adoptedDraftIds.isEmpty()) return;
        AiGenerationRecord record = recordService.getByRecordNo(recordNo, projectId);
        if (record == null || record.getStagePayload() == null) return;
        try {
            com.alibaba.fastjson.JSONObject payload = JSON.parseObject(record.getStagePayload());
            com.alibaba.fastjson.JSONArray pool = payload.getJSONArray("pool");
            if (pool == null || pool.isEmpty()) return;
            java.util.Set<String> toRemove = new java.util.HashSet<>(adoptedDraftIds);
            com.alibaba.fastjson.JSONArray newPool = new com.alibaba.fastjson.JSONArray();
            for (int i = 0; i < pool.size(); i++) {
                com.alibaba.fastjson.JSONObject item = pool.getJSONObject(i);
                if (item == null) continue;
                String draftId = item.getString("draftId");
                if (draftId != null && toRemove.contains(draftId)) continue; // 跳过已采纳
                newPool.add(item);
            }
            payload.put("pool", newPool);
            recordService.updateStage(recordNo, projectId, record.getCurrentStage(), payload.toJSONString());
            log.info("[采纳] 已从 pool 移除 {} 条已采纳草稿 | recordNo={}", adoptedDraftIds.size(), recordNo);
        } catch (Exception e) {
            log.warn("[采纳] pool 移除失败 | recordNo={} err={}", recordNo, e.getMessage());
        }
    }

    public Map<String, Object> getRecordState(String recordNo, Integer projectId) {
        AiGenerationRecord record = recordService.getByRecordNo(recordNo, projectId);
        if (record == null) return null;
        Map<String, Object> state = new HashMap<>();
        state.put("recordNo", record.getRecordNo());
        state.put("currentStage", record.getCurrentStage());
        state.put("stagePayload", record.getStagePayload());
        // 维度清单面板数据（旧记录无 dimensions 时由 builder 从 testPoints+pool+audit 现算）
        state.put("dimensions", buildDimensionsFromPayload(record.getStagePayload()));
        java.util.List<java.util.Map<String, Object>> rounds = recordService.parseRounds(record.getOutputSnapshot());
        state.put("rounds", rounds);
        if (!rounds.isEmpty()) {
            state.put("latestRound", rounds.get(rounds.size() - 1));
        }
        return state;
    }

    /** 从 stage_payload 派生维度清单（testPoints + pool + audit + exhausted 标记） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildDimensionsFromPayload(String stagePayload) {
        if (stagePayload == null) return new ArrayList<>();
        try {
            com.alibaba.fastjson.JSONObject payload = JSON.parseObject(stagePayload);
            com.alibaba.fastjson.JSONArray tpArr = payload.getJSONArray("testPoints");
            if (tpArr == null || tpArr.isEmpty()) return new ArrayList<>();
            java.util.List<java.util.Map<String, Object>> testPoints = new ArrayList<>();
            for (int i = 0; i < tpArr.size(); i++) {
                testPoints.add(new HashMap<>(tpArr.getJSONObject(i)));
            }
            com.alibaba.fastjson.JSONArray pool = payload.getJSONArray("pool");
            Map<String, Object> audit = null;
            String auditJson = payload.getString("audit");
            if (auditJson != null) {
                audit = JSON.parseObject(auditJson);
            }
            boolean exhausted = payload.getBooleanValue("autoLoopExhausted");
            return DimensionBoardBuilder.build(testPoints, pool, audit, exhausted);
        } catch (Exception e) {
            log.warn("维度清单派生失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== 工具 ====================

    private void send(SseEmitter emitter, String event, String data) {
        try {
            // delta 事件 JSON 编码，防止 Markdown 中的 \n 破坏 SSE 帧格式
            if ("delta".equals(event)) {
                data = JSON.toJSONString(data);
            }
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            // 客户端断开，忽略
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max);
    }

    /**
     * Fallback：AI 在 thinking 模式下可能直接输出 test_cases 而非 test_points。
     * 从 test_cases 的 coverPoints 推断测试点，将已生成用例直接纳入草稿池，跳过逐点生成。
     */
    @SuppressWarnings("unchecked")
    private void handleCasesAsPoints(SkillRequest request, SseEmitter emitter, AiStreamBuffer buffer,
                                      AiGenerationRecord record, String full,
                                      String recordNo, Integer projectId) {
        DraftGenResult<CaseDraftDTO> genResult = parseCaseGenResult(full);
        List<CaseDraftDTO> drafts = genResult.getDrafts();
        assignDraftIds(drafts);
        // 从 coverPoints 推断测试点
        java.util.Map<String, Map<String, Object>> tpMap = new java.util.LinkedHashMap<>();
        for (CaseDraftDTO d : drafts) {
            if (d.getCoverPoints() == null) continue;
            for (String tpId : d.getCoverPoints()) {
                if (!tpMap.containsKey(tpId)) {
                    Map<String, Object> tp = new java.util.LinkedHashMap<>();
                    tp.put("id", tpId);
                    tp.put("title", d.getCaseName() != null ? d.getCaseName() : tpId);
                    tp.put("category", inferCategoryFromDesignMethod(d.getDesignMethod()));
                    tp.put("priority", d.getPriority() != null ? d.getPriority() : "P1");
                    tp.put("status", "DONE");
                    tp.put("caseDraftIds", new java.util.ArrayList<String>());
                    tpMap.put(tpId, tp);
                }
            }
        }
        List<Map<String, Object>> tps = new java.util.ArrayList<>(tpMap.values());
        // 关联草稿 ID 到测试点
        for (CaseDraftDTO d : drafts) {
            if (d.getCoverPoints() == null) continue;
            for (String tpId : d.getCoverPoints()) {
                Map<String, Object> tp = tpMap.get(tpId);
                if (tp != null) {
                    ((List<String>) tp.get("caseDraftIds")).add(d.getDraftId());
                }
            }
        }
        addDraftsToPoolSync(recordNo, projectId, drafts, tps);
        pushDimensions(emitter, tps, null, false);
        for (CaseDraftDTO d : drafts) send(emitter, "case", JSON.toJSONString(d));
        // 跳过逐点生成，直接进入收尾
        afterAllPointsDone(request, emitter, buffer, record, tps);
    }

    private String inferCategoryFromDesignMethod(String designMethod) {
        if (designMethod == null) return "FUNCTION";
        switch (designMethod) {
            case "BOUNDARY": return "BOUNDARY";
            case "ERROR_GUESSING": return "EXCEPTION";
            default: return "FUNCTION";
        }
    }
}
