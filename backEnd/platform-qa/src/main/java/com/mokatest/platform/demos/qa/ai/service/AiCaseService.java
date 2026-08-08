package com.mokatest.platform.demos.qa.ai.service;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson.JSON;
import com.mokatest.platform.demos.ai.domain.AiGenerationRecord;
import com.mokatest.platform.demos.ai.gateway.AiGatewayService;
import com.mokatest.platform.demos.ai.gateway.ChatMessage;
import com.mokatest.platform.demos.ai.gateway.ChatOptions;
import com.mokatest.platform.demos.ai.service.AiGenerationRecordService;
import com.mokatest.platform.demos.ai.skill.AiSkill;
import com.mokatest.platform.demos.ai.skill.DraftGenResult;
import com.mokatest.platform.demos.ai.skill.SkillContext;
import com.mokatest.platform.demos.ai.skill.SkillExecutionSupport;
import com.mokatest.platform.demos.ai.skill.SkillRegistry;
import com.mokatest.platform.demos.ai.router.AiIntentRouter;
import com.mokatest.platform.demos.ai.skill.ConversationHistory;
import com.mokatest.platform.demos.ai.skill.SkillRequest;
import com.mokatest.platform.demos.ai.stream.AiStreamBuffer;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.qa.ai.casegen.CaseDraftDTO;
import com.mokatest.platform.demos.qa.ai.casegen.CaseDraftMapper;
import com.mokatest.platform.demos.qa.ai.casegen.GenerateTestCaseSkill;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import com.mokatest.platform.demos.qa.service.TestCaseService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
 *
 * 会话隔离：generate 创建新记录；append/adopt 只传 recordNo，
 * 锚定需求（entityId）与项目归属一律从记录读取，不信前端传参。
 */
@Service
public class AiCaseService {

    private static final Logger log = LoggerFactory.getLogger(AiCaseService.class);

    @Autowired
    private SkillExecutionSupport executionSupport;

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private AiGatewayService gatewayService;

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
    private com.mokatest.platform.demos.ai.router.AiIntentRouter intentRouter;

    /**
     * 流式生成（SSE）
     *
     * @param request    生成请求（entityId=需求ID；append 时 recordNo 必填）
     * @param append     true=追加生成（沿用既有记录）；false=新会话
     * @param emitter    SSE 通道
     */
    @SuppressWarnings("unchecked")
    public void generateStream(SkillRequest request, boolean append, SseEmitter emitter) {
        AiSkill<DraftGenResult<CaseDraftDTO>> skill = skillRegistry.getSkill(GenerateTestCaseSkill.SCENE);
        String previousOutput = null;
        AiGenerationRecord record = null;

        if (append) {
            record = recordService.requireValid(request.getRecordNo(), request.getProjectId());
            request.setEntityId(record.getEntityId());
            request.setProjectId(record.getProjectId());
            previousOutput = latestRoundDrafts(record.getOutputSnapshot());
        }

        // 对话历史注入：让模型感知用户的修正与补充信息
        if (record != null) {
            request.setHistory(ConversationHistory.build(recordService.parseRounds(record.getOutputSnapshot())));
        }

        // 知识库（L0）检索词：需求标题 + 描述摘要（KnowledgeSource 在 buildContext 内消费）
        try {
            Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                    .eq("id", request.getEntityId())
                    .eq("project_id", request.getProjectId())
                    .select("id", "title", "description"));
            if (req != null && req.getTitle() != null) {
                String desc = com.mokatest.platform.demos.qa.ai.util.RichTextCleaner.toPlainText(req.getDescription());
                request.setRetrievalQuery(req.getTitle() + " " + abbreviate(desc == null ? "" : desc, 200));
            }
        } catch (Exception ignore) {
            // 检索词组装失败不影响主流程（KnowledgeSource 回退用指令检索）
        }

        SkillContext context = executionSupport.buildContext(skill, request);

        ChatOptions options = new ChatOptions();
        options.setScene(GenerateTestCaseSkill.SCENE);
        options.setUserId(request.getUserId());
        options.setTeamId(request.getTeamId());
        options.setProjectId(request.getProjectId());
        options.setPromptSummary("需求#" + request.getEntityId()
                + (request.getInstruction() != null ? " 指令:" + abbreviate(request.getInstruction(), 80) : ""));

        // 发送即落库：新会话先建记录；意图路由决定本轮走问答还是生成
        if (record == null) {
            record = executionSupport.createRecord(request, GenerateTestCaseSkill.SCENE, null, context);
            request.setRecordNo(record.getRecordNo());
        }
        // 发送即落库：先写入 GENERATING 轮再做意图分类，缩短首字节等待；
        // 轮次类型先按 gen 记，QA 轮完成时由 finishQaRound 纠正为 qa
        recordService.beginRound(record.getRecordNo(), request.getProjectId(), request.getInstruction(), "gen");
        AiIntentRouter.Intent intent = intentRouter.route(request.getInstruction(), options);

        final AiGenerationRecord finalRecord = record;
        final SkillContext finalContext = context;

        // 流缓冲：边推边累积，支持关弹窗后重连续流；rawText 每 2s 落库
        final AiStreamBuffer buffer = bufferRegistry.create(record.getRecordNo());
        final String recordNo = record.getRecordNo();
        final long[] lastFlush = {System.currentTimeMillis()};

        if (intent == AiIntentRouter.Intent.QA) {
            qaStream(request, context, finalRecord, buffer, options, emitter);
            return;
        }

        List<ChatMessage> messages = skill.buildPrompt(request, context, previousOutput);

        gatewayService.chatStream(messages, options,
                chunk -> {
                    buffer.append(chunk);
                    send(emitter, "delta", chunk);
                    long now = System.currentTimeMillis();
                    if (now - lastFlush[0] > 2000) {
                        lastFlush[0] = now;
                        recordService.updateRoundRawText(recordNo, request.getProjectId(), buffer.text());
                    }
                },
                full -> handleComplete(emitter, skill, request, finalRecord, finalContext, buffer, full),
                error -> {
                    if (buffer.isCancelRequested()) {
                        // 用户主动停止：保留已生成部分内容
                        recordService.stopRound(recordNo, request.getProjectId(), buffer.text());
                        buffer.markStopped();
                        send(emitter, "stopped", "已停止");
                    } else {
                        recordService.failRound(recordNo, request.getProjectId(), error.getMessage());
                        buffer.fail(error.getMessage());
                        send(emitter, "error", error.getMessage());
                    }
                    emitter.complete();
                },
                call -> buffer.bindCall(call));
    }

    /**
     * 问答轮：带 L1-L5 上下文 + 对话历史的自由问答（不出草稿、不入库）
     */
    private void qaStream(SkillRequest request, SkillContext context, AiGenerationRecord record,
                          AiStreamBuffer buffer, ChatOptions options, SseEmitter emitter) {
        String recordNo = record.getRecordNo();
        StringBuilder user = new StringBuilder();
        user.append(context.render());
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            user.append("\n## 本会话对话历史\n").append(request.getHistory()).append('\n');
        }
        user.append("\n用户问题：").append(request.getInstruction());
        List<ChatMessage> messages = List.of(
                ChatMessage.system("你是资深测试专家助手。请基于给定的需求上下文和对话历史回答用户问题。"
                        + "回答使用中文，简洁专业，可以使用 markdown 列表。"
                        + "不要输出 JSON 用例数组；如果用户的问题其实是在要求生成用例，"
                        + "也只需用文字给出设计建议即可。"),
                ChatMessage.user(user.toString()));

        final long[] lastFlush = {System.currentTimeMillis()};
        gatewayService.chatStream(messages, options,
                chunk -> {
                    buffer.append(chunk);
                    send(emitter, "delta", chunk);
                    long now = System.currentTimeMillis();
                    if (now - lastFlush[0] > 2000) {
                        lastFlush[0] = now;
                        recordService.updateRoundRawText(recordNo, request.getProjectId(), buffer.text());
                    }
                },
                full -> {
                    recordService.finishQaRound(recordNo, request.getProjectId(), full);
                    java.util.Map<String, Object> result = new HashMap<>();
                    result.put("recordNo", recordNo);
                    result.put("type", "qa");
                    result.put("drafts", List.of());
                    String resultJson = JSON.toJSONString(result);
                    buffer.finish(resultJson);
                    send(emitter, "result", resultJson);
                    emitter.complete();
                },
                error -> {
                    if (buffer.isCancelRequested()) {
                        recordService.stopRound(recordNo, request.getProjectId(), buffer.text());
                        buffer.markStopped();
                        send(emitter, "stopped", "已停止");
                    } else {
                        recordService.failRound(recordNo, request.getProjectId(), error.getMessage());
                        buffer.fail(error.getMessage());
                        send(emitter, "error", error.getMessage());
                    }
                    emitter.complete();
                },
                call -> buffer.bindCall(call));
    }

    /**
     * 停止生成：取消底层模型调用，当前轮次标记 STOPPED 并保留部分内容
     */
    public void stop(String recordNo) {
        Integer projectId = ProjectContextHolder.getProjectId();
        AiGenerationRecord record = recordService.requireValid(recordNo, projectId);
        AiStreamBuffer buffer = bufferRegistry.get(record.getRecordNo());
        if (buffer != null && buffer.getState() == AiStreamBuffer.State.STREAMING) {
            buffer.requestCancel();
        } else {
            // 缓冲已不在（如已结束），兜底直接标记
            recordService.stopRound(record.getRecordNo(), projectId, null);
        }
    }

    /**
     * 重新生成：删除最后一轮，按同一指令重跑（SSE）
     *
     * @param baseRequest 请求线程内已解析好上下文（userId/teamId/projectId）的基础请求
     */
    public void regenerateStream(SkillRequest baseRequest, SseEmitter emitter) {
        String recordNo = baseRequest.getRecordNo();
        AiGenerationRecord record = recordService.requireValid(recordNo, baseRequest.getProjectId());
        com.alibaba.fastjson.JSONArray rounds = recordService.parseRounds(record.getOutputSnapshot());
        if (rounds.isEmpty()) {
            throw new BusinessException("没有可重新生成的轮次");
        }
        String instruction = rounds.getJSONObject(rounds.size() - 1).getString("instruction");
        recordService.removeLastRound(recordNo, baseRequest.getProjectId());
        baseRequest.setInstruction(instruction);
        generateStream(baseRequest, true, emitter);
    }

    /**
     * 删除会话记录（不影响已入库的用例）
     */
    public void deleteRecord(String recordNo) {
        recordService.delete(recordNo, ProjectContextHolder.getProjectId());
    }

    /** 流式完成后解析 → 完成轮次 → 推送结果事件 */
    private void handleComplete(SseEmitter emitter, AiSkill<DraftGenResult<CaseDraftDTO>> skill, SkillRequest request,
                                AiGenerationRecord record, SkillContext context, AiStreamBuffer buffer, String full) {
        try {
            DraftGenResult<CaseDraftDTO> genResult;
            try {
                genResult = skill.parse(full);
            } catch (BusinessException parseError) {
                // 同步重试一次：把完整输出带回让模型自我修正
                SkillContext retryCtx = executionSupport.buildContext(skill, request);
                List<ChatMessage> retryMessages = skill.buildPrompt(request, retryCtx, null);
                retryMessages.add(ChatMessage.assistant(full));
                retryMessages.add(ChatMessage.user("上一次输出无法解析：" + parseError.getMessage()
                        + "，请严格按要求的 JSON 对象格式重新输出。"));
                ChatOptions retryOptions = new ChatOptions();
                retryOptions.setScene(GenerateTestCaseSkill.SCENE);
                retryOptions.setUserId(request.getUserId());
                retryOptions.setTeamId(request.getTeamId());
                retryOptions.setProjectId(request.getProjectId());
                retryOptions.setPromptSummary("解析重试");
                genResult = skill.parse(gatewayService.chat(retryMessages, retryOptions).getContent());
            }

            List<CaseDraftDTO> drafts = genResult.getDrafts();
            assignDraftIds(drafts);
            recordService.finishRound(record.getRecordNo(), request.getProjectId(),
                    JSON.toJSONString(drafts), full, JSON.toJSONString(genResult.getUncertainties()),
                    JSON.toJSONString(request.getCitations()));

            Map<String, Object> result = new HashMap<>();
            result.put("recordNo", record.getRecordNo());
            result.put("drafts", drafts);
            result.put("uncertainties", genResult.getUncertainties());
            result.put("citations", request.getCitations());
            String resultJson = JSON.toJSONString(result);
            buffer.finish(resultJson);
            send(emitter, "result", resultJson);
            emitter.complete();
        } catch (Exception e) {
            log.warn("AI 生成用例解析失败: {}", e.getMessage());
            recordService.failRound(record.getRecordNo(), request.getProjectId(),
                    e instanceof BusinessException ? e.getMessage() : "生成结果解析失败");
            buffer.fail(e.getMessage());
            send(emitter, "error", e instanceof BusinessException ? e.getMessage() : "生成结果解析失败，请重试");
            emitter.complete();
        }
    }

    /**
     * 采纳入库：勾选草稿 → 走 testCase/save 批量入库
     *
     * 防重复：draftId 已在采纳明细中的条目跳过；与已存在用例同名的返回提示（不拦截）。
     *
     * @param recordNo 生成记录编号
     * @param items    前端确认后的草稿（可编辑过）
     * @return 入库结果：savedIds / adoptedDraftIds / skippedCount / duplicateNames
     */
    public Map<String, Object> adopt(String recordNo, List<CaseDraftDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("请至少选择一条用例入库");
        }
        Integer projectId = ProjectContextHolder.getProjectId();
        AiGenerationRecord record = recordService.requireValid(recordNo, projectId);
        // 锚定需求从记录读取，不信前端
        Long requirementId = record.getEntityId();
        Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", requirementId)
                .eq("project_id", projectId)
                .select("id", "module_id"));
        if (req == null) {
            throw new BusinessException("锚定需求不存在或已被删除");
        }

        // 1. 过滤已采纳过的草稿（draftId 防线）
        java.util.Set<String> adoptedIds = recordService.parseAdoptedDraftIds(record.getAdoptedDetail());
        List<CaseDraftDTO> toSave = new ArrayList<>();
        int skipped = 0;
        for (CaseDraftDTO draft : items) {
            if (draft.getCaseName() == null || draft.getCaseName().trim().isEmpty()) {
                continue;
            }
            if (draft.getDraftId() != null && adoptedIds.contains(draft.getDraftId())) {
                skipped++;
                continue;
            }
            toSave.add(draft);
        }

        // 2. 内容级查重（同需求下同名用例，软提示不拦截）
        List<String> duplicateNames = new ArrayList<>();
        if (!toSave.isEmpty()) {
            List<TestCase> existing = testCaseService.list(
                    new QueryWrapper<TestCase>()
                            .eq("project_id", projectId)
                            .eq("requirement_id", requirementId)
                            .select("id", "case_name"));
            java.util.Set<String> existingNames = new java.util.HashSet<>();
            for (TestCase c : existing) {
                if (c.getCaseName() != null) {
                    existingNames.add(c.getCaseName().trim());
                }
            }
            for (CaseDraftDTO draft : toSave) {
                if (existingNames.contains(draft.getCaseName().trim())) {
                    duplicateNames.add(draft.getCaseName().trim());
                }
            }
        }

        // 3. 入库并登记采纳明细
        List<Integer> savedIds = new ArrayList<>();
        List<String> adoptedDraftIds = new ArrayList<>();
        com.alibaba.fastjson.JSONArray entries = new com.alibaba.fastjson.JSONArray();
        for (CaseDraftDTO draft : toSave) {
            TestCase testCase = caseDraftMapper.toEntity(draft, projectId,
                    requirementId.intValue(), req.getModuleId());
            cn.dev33.satoken.util.SaResult saveResult = testCaseService.saveOrUpdateCase(testCase);
            if (saveResult.getCode() != 200) {
                throw new BusinessException("用例入库失败：" + saveResult.getMsg());
            }
            Integer caseId = (Integer) saveResult.getData();
            savedIds.add(caseId);
            com.alibaba.fastjson.JSONObject entry = new com.alibaba.fastjson.JSONObject();
            entry.put("draftId", draft.getDraftId());
            entry.put("caseId", caseId);
            entries.add(entry);
            if (draft.getDraftId() != null) {
                adoptedDraftIds.add(draft.getDraftId());
            }
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

    /** 生成后为每条草稿分配身份标识（防重复入库） */
    private void assignDraftIds(List<CaseDraftDTO> drafts) {
        for (CaseDraftDTO draft : drafts) {
            if (draft.getDraftId() == null || draft.getDraftId().isEmpty()) {
                draft.setDraftId(java.util.UUID.randomUUID().toString().replace("-", ""));
            }
        }
    }

    /**
     * 查询某需求下的生成记录（回溯）
     */
    public List<AiGenerationRecord> listRecords(Long requirementId) {
        Integer projectId = ProjectContextHolder.getProjectId();
        return recordService.listByEntity(projectId, GenerateTestCaseSkill.SCENE, requirementId);
    }

    /**
     * 从轮次快照中取最新一轮的草稿 JSON（作为追加生成的"上一轮输出"）
     */
    private String latestRoundDrafts(String outputSnapshot) {
        com.alibaba.fastjson.JSONArray rounds = recordService.parseRounds(outputSnapshot);
        if (rounds.isEmpty()) {
            return null;
        }
        com.alibaba.fastjson.JSONObject latest = rounds.getJSONObject(rounds.size() - 1);
        return latest == null ? null : latest.getString("drafts");
    }

    /** 组装当前请求上下文（teamId/projectId/userId 全部来自服务端上下文，不信前端 body） */
    public SkillRequest buildRequest(Long requirementId, String recordNo, String instruction) {
        Integer projectId = ProjectContextHolder.getProjectId();
        if (projectId == null) {
            throw new BusinessException("缺少项目上下文（X-Project-Id）");
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

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            // 客户端断开，忽略
        }
    }

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
