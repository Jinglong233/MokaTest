package com.mokatest.platform.demos.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.config.ModelManager;
import com.mokatest.platform.demos.ai.context.ContextBlocks;
import com.mokatest.platform.demos.ai.domain.AiGenerationRecord;
import com.mokatest.platform.demos.ai.domain.Citation;
import com.mokatest.platform.demos.ai.service.AssistantFactory;
import com.mokatest.platform.demos.ai.service.CaseGenerationAssistant;
import com.mokatest.platform.demos.ai.service.Intent;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import com.mokatest.platform.demos.ai.skill.DraftGenResult;
import com.mokatest.platform.demos.ai.skill.SkillRequest;
import com.mokatest.platform.demos.ai.stream.AiStreamBuffer;
import com.mokatest.platform.demos.ai.skill.apicase.ApiCaseDraftDTO;
import com.mokatest.platform.demos.ai.skill.apicase.ApiCaseDraftMapper;
import com.mokatest.platform.demos.ai.skill.apicase.ApiCaseDraftParser;
import com.mokatest.platform.demos.ai.skill.apicase.ApiDefinitionSource;
import com.mokatest.platform.demos.ai.skill.apicase.ExistingApiCaseSource;
import com.mokatest.platform.demos.ai.skill.apicase.SiblingApiSource;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiNodeType;
import com.mokatest.platform.demos.api.domain.dto.AddApiInterfaceDTO;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import com.mokatest.platform.demos.api.service.ApiRequestService;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder;
import com.mokatest.platform.demos.exception.BusinessException;
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
 * AI 生成 API 接口用例服务（场景四）
 *
 * 会话隔离：append/adopt 只传 recordNo，锚定接口（entityId）与项目归属从记录读取。
 * 数据边界：接口查询带 project_id + team_id 双过滤。
 *
 * 重写后：用 LangChain4j CaseGenerationAssistant（AI Service + TokenStream + RAG）。
 */
@Service
public class AiApiCaseService {

    private static final Logger log = LoggerFactory.getLogger(AiApiCaseService.class);

    @Autowired private ApiDefinitionSource apiDefinitionSource;
    @Autowired private SiblingApiSource siblingApiSource;
    @Autowired private ExistingApiCaseSource existingApiCaseSource;

    private final ApiCaseDraftParser parser = new ApiCaseDraftParser();

    @Autowired private ModelManager modelManager;
    @Autowired private AssistantFactory assistantFactory;
    @Autowired private AiGenerationRecordService recordService;
    @Autowired private ApiCaseDraftMapper apiCaseDraftMapper;
    @Autowired private ApiRequestMapper apiRequestMapper;
    @Autowired private ApiRequestService apiRequestService;
    @Autowired private com.mokatest.platform.demos.ai.stream.AiStreamBufferRegistry bufferRegistry;
    @Autowired private com.mokatest.platform.demos.ai.knowledge.KnowledgeService knowledgeService;

    @SuppressWarnings("unchecked")
    public void generateStream(SkillRequest request, boolean append, SseEmitter emitter) {
        String previousOutput = null;
        AiGenerationRecord record = null;

        if (append) {
            record = recordService.requireValid(request.getRecordNo(), request.getProjectId());
            request.setEntityId(record.getEntityId());
            request.setProjectId(record.getProjectId());
            request.setTeamId(record.getTeamId());
            previousOutput = latestRoundDrafts(record.getOutputSnapshot());
        }

        if (record != null) {
            request.setHistory(buildHistory(recordService.parseRounds(record.getOutputSnapshot())));
        }

        List<String> trimmedNotes = new ArrayList<>();
        String contextText = buildContextText(request);

        if (record == null) {
            record = createRecord(request, ApiCaseGenPrompts.SCENE, trimmedNotes);
            request.setRecordNo(record.getRecordNo());
        }
        recordService.beginRound(record.getRecordNo(), request.getProjectId(), request.getInstruction(), "gen");
        Intent intent;
        try {
            intent = assistantFactory.createClassifier().classify(request.getInstruction());
        } catch (Exception e) {
            log.warn("意图分类失败，按生成处理: {}", e.getMessage());
            intent = Intent.GENERATE;
        }

        final AiGenerationRecord finalRecord = record;
        final AiStreamBuffer buffer = bufferRegistry.create(record.getRecordNo());
        final String recordNo = record.getRecordNo();

        if (intent == Intent.QA) {
            qaStream(request, contextText, finalRecord, buffer, emitter);
            return;
        }

        int count = ApiCaseGenPrompts.resolveCount(request.getInstruction(), request.getCount(), 5);
        String userMsg = ApiCaseGenPrompts.buildUserMessage(
                contextText, request.getHistory(), request.getInstruction(), previousOutput, count);

        CaseGenerationAssistant assistant = assistantFactory.createCaseGenerator(
                request.getProjectId(), ApiCaseGenPrompts.systemPrompt());
        runStream(assistant.generate(userMsg), buffer, emitter, recordNo, request.getProjectId(),
                request.getCitations(),
                full -> handleComplete(emitter, request, finalRecord, buffer, full));
    }

    private void qaStream(SkillRequest request, String contextText, AiGenerationRecord record,
                          AiStreamBuffer buffer, SseEmitter emitter) {
        String recordNo = record.getRecordNo();
        String userMsg = ApiCaseGenPrompts.buildQaUserMessage(
                contextText, request.getHistory(), request.getInstruction());

        CaseGenerationAssistant assistant = assistantFactory.createCaseGenerator(
                request.getProjectId(), ApiCaseGenPrompts.qaSystemPrompt());
        runStream(assistant.generate(userMsg), buffer, emitter, recordNo, request.getProjectId(),
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

    @SuppressWarnings("unchecked")
    private void handleComplete(SseEmitter emitter, SkillRequest request, AiGenerationRecord record,
                                AiStreamBuffer buffer, String full) {
        String recordNo = record.getRecordNo();
        try {
            DraftGenResult<ApiCaseDraftDTO> genResult;
            try {
                genResult = parser.parse(full);
            } catch (BusinessException parseError) {
                List<ChatMessage> retry = new ArrayList<>();
                retry.add(AiMessage.from(full));
                retry.add(UserMessage.from("上一次输出无法解析：" + parseError.getMessage()
                        + "，请严格按要求的 JSON 对象格式重新输出。"));
                genResult = parser.parse(chatSync(retry));
            }

            List<ApiCaseDraftDTO> drafts = genResult.getDrafts();
            assignDraftIds(drafts);
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
            log.warn("AI 生成接口用例解析失败: {}", e.getMessage());
            recordService.failRound(recordNo, request.getProjectId(),
                    e instanceof BusinessException ? e.getMessage() : "生成结果解析失败", buffer.text());
            buffer.fail(e.getMessage());
            send(emitter, "error", e instanceof BusinessException ? e.getMessage() : "生成结果解析失败，请重试");
            emitter.complete();
        }
    }

    // ==================== TokenStream 通用桥接 ====================

    private void runStream(TokenStream ts, AiStreamBuffer buffer, SseEmitter emitter,
                            String recordNo, Integer projectId,
                            java.util.List<Citation> citationsCollector,
                            java.util.function.Consumer<String> onComplete) {
        final long[] lastFlush = {System.currentTimeMillis()};
        ts.onPartialResponse(chunk -> {
                    if (buffer.isCancelRequested() || chunk == null || chunk.isEmpty()) return;
                    buffer.append(chunk);
                    send(emitter, "delta", chunk);
                    long now = System.currentTimeMillis();
                    if (now - lastFlush[0] > 2000) {
                        lastFlush[0] = now;
                        recordService.updateRoundRawText(recordNo, projectId, buffer.text());
                    }
                })
                .onRetrieved(contents -> {
                    for (Content c : contents) {
                        try {
                            String docIdStr = c.textSegment().metadata().getString("docId");
                            String docTitle = c.textSegment().metadata().getString("docTitle");
                            if (docIdStr != null && citationsCollector != null) {
                                citationsCollector.add(new Citation(
                                        Long.valueOf(docIdStr), docTitle,
                                        abbreviate(c.textSegment().text(), 100)));
                            }
                        } catch (Exception ignore) {
                        }
                    }
                })
                .onCompleteResponse(response -> {
                    String full = buffer.text();
                    if (buffer.isCancelRequested()) {
                        recordService.stopRound(recordNo, projectId, full);
                        buffer.markStopped();
                        send(emitter, "stopped", "已停止");
                        emitter.complete();
                    } else {
                        onComplete.accept(full);
                    }
                })
                .onError(error -> {
                    if (buffer.isCancelRequested()) {
                        recordService.stopRound(recordNo, projectId, buffer.text());
                        buffer.markStopped();
                        send(emitter, "stopped", "已停止");
                    } else {
                        recordService.failRound(recordNo, projectId, error.getMessage(), buffer.text());
                        buffer.fail(error.getMessage());
                        send(emitter, "error", error.getMessage() != null ? error.getMessage() : "生成失败");
                    }
                    emitter.complete();
                })
                .start();
    }

    // ==================== 上下文组装 ====================

    private String buildContextText(SkillRequest request) {
        Long eid = request.getEntityId();
        Integer pid = request.getProjectId();
        Integer tid = request.getTeamId();
        List<String> trimmedNotes = new ArrayList<>();
        List<ContextBlocks.Block> blocks = new ArrayList<>();
        ContextBlocks.Block l1 = apiDefinitionSource.load(eid, pid, tid);
        if (l1 != null) blocks.add(l1);
        addIfNotNull(blocks, loadSafely(() -> siblingApiSource.load(eid, pid), "L3"));
        addIfNotNull(blocks, loadSafely(() -> existingApiCaseSource.load(eid, pid), "L4"));
        return ContextBlocks.assemble(blocks, trimmedNotes);
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

    private String buildHistory(java.util.List<java.util.Map<String, Object>> rounds) {
        if (rounds == null || rounds.isEmpty()) return null;
        int maxRounds = 6;
        int maxOutputSummary = 300;
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, rounds.size() - maxRounds);
        for (int i = start; i < rounds.size(); i++) {
            java.util.Map<String, Object> round = rounds.get(i);
            if (round == null) continue;
            String instruction = str(round, "instruction");
            if (instruction != null && !instruction.isEmpty()) {
                sb.append("用户：").append(instruction).append('\n');
            }
            String status = str(round, "status");
            String type = str(round, "type");
            if ("qa".equals(type)) {
                String answer = str(round, "rawText");
                if (answer != null && !answer.isEmpty()) {
                    sb.append("助手：").append(answer.length() <= maxOutputSummary ? answer : answer.substring(0, maxOutputSummary) + "…").append('\n');
                }
            } else if ("DONE".equals(status)) {
                Object draftsObj = round.get("drafts");
                java.util.List<?> drafts = draftsObj instanceof java.util.List ? (java.util.List<?>) draftsObj : null;
                if (drafts != null && !drafts.isEmpty()) {
                    sb.append("助手：生成了 ").append(drafts.size()).append(" 条用例草稿（");
                    int limit = Math.min(drafts.size(), 5);
                    for (int j = 0; j < limit; j++) {
                        Object o = drafts.get(j);
                        String name = o instanceof java.util.Map ? str((java.util.Map<String, Object>) o, "caseName") : null;
                        if (name != null) {
                            sb.append(name);
                            if (j < limit - 1) sb.append("、");
                        }
                    }
                    if (drafts.size() > 5) sb.append(" 等");
                    sb.append("）\n");
                }
            } else if ("FAILED".equals(status)) {
                sb.append("助手：上一轮生成失败\n");
            } else if ("STOPPED".equals(status)) {
                sb.append("助手：上一轮被用户手动停止\n");
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String str(java.util.Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    public void stop(String recordNo) {
        Integer projectId = ProjectContextHolder.getProjectId();
        AiGenerationRecord record = recordService.requireValid(recordNo, projectId);
        AiStreamBuffer buffer = bufferRegistry.get(record.getRecordNo());
        if (buffer != null && buffer.getState() == AiStreamBuffer.State.STREAMING) {
            buffer.requestCancel();
        } else {
            recordService.stopRound(record.getRecordNo(), projectId, null);
        }
    }

    public void regenerateStream(SkillRequest baseRequest, SseEmitter emitter) {
        String recordNo = baseRequest.getRecordNo();
        AiGenerationRecord record = recordService.requireValid(recordNo, baseRequest.getProjectId());
        java.util.List<java.util.Map<String, Object>> rounds = recordService.parseRounds(record.getOutputSnapshot());
        if (rounds.isEmpty()) {
            throw new BusinessException("没有可重新生成的轮次");
        }
        java.util.Map<String, Object> lastRound = rounds.get(rounds.size() - 1);
        String lastStatus = lastRound == null ? null : str(lastRound, "status");
        if (!"FAILED".equals(lastStatus) && !"DONE".equals(lastStatus)) {
            throw new BusinessException("仅允许对已完成或失败的轮次重新生成");
        }
        String instruction = lastRound == null ? null : str(lastRound, "instruction");
        recordService.removeLastRound(recordNo, baseRequest.getProjectId());
        baseRequest.setInstruction(instruction);
        generateStream(baseRequest, true, emitter);
    }

    public void deleteRecord(String recordNo) {
        recordService.delete(recordNo, ProjectContextHolder.getProjectId());
    }

    public Map<String, Object> adopt(String recordNo, List<ApiCaseDraftDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("请至少选择一条用例入库");
        }
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer teamId = TeamContextHolder.getTeamId();
        AiGenerationRecord record = recordService.requireValid(recordNo, projectId);
        Long apiId = record.getEntityId();
        ApiRequest base = apiRequestMapper.selectOne(new QueryWrapper<ApiRequest>()
                .eq("id", apiId).eq("project_id", projectId).eq("api_node", ApiNodeType.INTERFACE));
        if (base == null) {
            throw new BusinessException("锚定接口不存在或已被删除");
        }

        java.util.Set<String> adoptedIds = recordService.parseAdoptedDraftIds(record.getAdoptedDetail());
        List<ApiCaseDraftDTO> toSave = new ArrayList<>();
        int skipped = 0;
        for (ApiCaseDraftDTO draft : items) {
            if (draft.getCaseName() == null || draft.getCaseName().trim().isEmpty()) continue;
            if (draft.getDraftId() != null && adoptedIds.contains(draft.getDraftId())) {
                skipped++;
                continue;
            }
            toSave.add(draft);
        }

        List<String> duplicateNames = new ArrayList<>();
        if (!toSave.isEmpty()) {
            List<ApiRequest> existing = apiRequestMapper.selectList(new QueryWrapper<ApiRequest>()
                    .eq("project_id", projectId).eq("source_drat_id", apiId).select("id", "api_name"));
            java.util.Set<String> existingNames = new java.util.HashSet<>();
            for (ApiRequest c : existing) {
                if (c.getApiName() != null) existingNames.add(c.getApiName().trim());
            }
            for (ApiCaseDraftDTO draft : toSave) {
                if (existingNames.contains(draft.getCaseName().trim())) {
                    duplicateNames.add(draft.getCaseName().trim());
                }
            }
        }

        List<Integer> savedIds = new ArrayList<>();
        List<String> adoptedDraftIds = new ArrayList<>();
        java.util.List<java.util.Map<String, Object>> entries = new java.util.ArrayList<>();
        for (ApiCaseDraftDTO draft : toSave) {
            ApiRequest entity = apiCaseDraftMapper.toEntity(draft, base, projectId, teamId);
            AddApiInterfaceDTO dto = BeanUtil.copyProperties(entity, AddApiInterfaceDTO.class);
            dto.setSourceDratId(apiId.intValue());
            SaResult saveResult = apiRequestService.saveCase(dto);
            if (saveResult.getCode() != 200) {
                throw new BusinessException("用例入库失败：" + saveResult.getMsg());
            }
            Integer caseId = (Integer) saveResult.getData();
            savedIds.add(caseId);
            java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("draftId", draft.getDraftId());
            entry.put("caseId", caseId);
            entries.add(entry);
            if (draft.getDraftId() != null) adoptedDraftIds.add(draft.getDraftId());
        }
        if (savedIds.isEmpty() && skipped > 0) {
            throw new BusinessException("所选条目均已入库过，无需重复入库");
        }
        if (savedIds.isEmpty()) {
            throw new BusinessException("所选条目均无效（用例名称为空）");
        }
        recordService.mergeAdoption(recordNo, projectId, entries);

        Map<String, Object> result = new HashMap<>();
        result.put("savedIds", savedIds);
        result.put("adoptedDraftIds", adoptedDraftIds);
        result.put("skippedCount", skipped);
        result.put("duplicateNames", duplicateNames);
        return result;
    }

    private void assignDraftIds(List<ApiCaseDraftDTO> drafts) {
        for (ApiCaseDraftDTO draft : drafts) {
            if (draft.getDraftId() == null || draft.getDraftId().isEmpty()) {
                draft.setDraftId(java.util.UUID.randomUUID().toString().replace("-", ""));
            }
        }
    }

    public List<AiGenerationRecord> listRecords(Long apiId) {
        return recordService.listByEntity(ProjectContextHolder.getProjectId(), ApiCaseGenPrompts.SCENE, apiId);
    }

    private String latestRoundDrafts(String outputSnapshot) {
        java.util.List<java.util.Map<String, Object>> rounds = recordService.parseRounds(outputSnapshot);
        if (rounds.isEmpty()) return null;
        java.util.Map<String, Object> latest = rounds.get(rounds.size() - 1);
        if (latest == null) return null;
        Object drafts = latest.get("drafts");
        return drafts == null ? null : JSON.toJSONString(drafts);
    }

    public SkillRequest buildRequest(Long apiId, String recordNo, String instruction) {
        Integer projectId = ProjectContextHolder.getProjectId();
        if (projectId == null) {
            throw new BusinessException("缺少项目上下文（X-Project-Id）");
        }
        SkillRequest request = new SkillRequest();
        request.setUserId(StpUtil.getLoginIdAsString());
        request.setTeamId(TeamContextHolder.getTeamId());
        request.setProjectId(projectId);
        request.setEntityId(apiId);
        request.setRecordNo(recordNo);
        request.setInstruction(instruction);
        return request;
    }

    // ==================== 工具 ====================

    private String chatSync(List<ChatMessage> messages) {
        ChatRequest req = ChatRequest.builder().messages(messages).build();
        ChatResponse resp = modelManager.getChatModel().chat(req);
        return resp.aiMessage().text();
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
        }
    }

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max);
    }
}
