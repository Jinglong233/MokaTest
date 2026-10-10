package com.mokatest.platform.demos.qa.aireview;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mokatest.platform.demos.common.SuperAdminChecker;
import com.mokatest.platform.demos.ai.config.ModelManager;
import com.mokatest.platform.demos.ai.review.ExpertReviewResult;
import com.mokatest.platform.demos.ai.review.ParallelReviewService;
import com.mokatest.platform.demos.ai.review.ReviewExpert;
import com.mokatest.platform.demos.ai.skill.ReviewChecklist;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.qa.ai.util.RichTextCleaner;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 需求评审接口（契约 v2 — 检查表驱动，服务端确定性算分）
 *
 * 评审执行：langchain4j-agentic 并行工作流（4 个专家评审各自维度子集 → 服务端聚合）。
 * SSE 事件：expert_start / expert_retry / expert_done / aggregating / done / reused / error / ping。
 */
@Slf4j
@RestController
@RequestMapping("/qa/ai/review")
@RequiredArgsConstructor
@SaCheckPermission("qa:ai:use")
public class AiReviewController {

    private final ModelManager modelManager;
    private final ParallelReviewService parallelReviewService;
    private final RequirementMapper requirementMapper;
    private final AiRequirementReviewMapper reviewMapper;
    private final SuperAdminChecker superAdminChecker;
    @org.springframework.beans.factory.annotation.Autowired
    private com.mokatest.platform.demos.ai.stream.AiStreamBufferRegistry bufferRegistry;

    /** buffer key 前缀 */
    private static final String BUFFER_KEY = "review_";

    private static final ConcurrentHashMap<Long, ActiveReview> ACTIVE = new ConcurrentHashMap<>();

    /** SSE 心跳：评审期间每 15s 推 ping 事件，防中间代理掐空闲连接 + 前端可感知存活 */
    private static final java.util.concurrent.ScheduledExecutorService HEARTBEAT =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ai-review-heartbeat");
                t.setDaemon(true);
                return t;
            });

    @Data
    static class ActiveReview {
        /** 各专家状态：RUNNING / RETRYING / DONE / FAILED；aggregating=聚合阶段 */
        final Map<String, String> expertStatus = new ConcurrentHashMap<>();
        volatile boolean aggregating;
        String createUserId;
        /** 评审记录 ID（续传用） */
        Long reviewId;
        /** SSE emitter（attach 时替换为新的） */
        volatile SseEmitter emitter;
    }

    @Data
    public static class ReviewRequest {
        private Long requirementId;
        private Boolean force;
        private String instruction;
    }

    @Data
    static class ReviewScoreResult {
        private boolean valid;
        private String feedback;
        private String scoresJson;
        private Integer overall;
        private String confidence;
        private String checklistJson;
        private String risksJson;
        private String summary;
    }

    // ==================== SSE 流式评审 ====================

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ReviewRequest body) {
        SseEmitter emitter = new SseEmitter(300_000L);
        // 超时不能静默断流（前端会永远卡在"评审中"），主动推 error 事件
        emitter.onTimeout(() -> {
            log.warn("评审 SSE 超时(300s)");
            send(emitter, "error", "评审超时，请重试");
            safeComplete(emitter);
        });
        Integer projectId = ProjectContextHolder.getProjectId();
        String userId = StpUtil.getLoginIdAsString();
        Long requirementId = body.getRequirementId();

        if (requirementId == null) {
            runAsync(() -> sendError(emitter, "请指定需求ID"), emitter);
            return emitter;
        }

        Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", requirementId)
                .eq("project_id", projectId)
                .select("id", "title", "description"));
        if (req == null) {
            runAsync(() -> sendError(emitter, "需求不存在"), emitter);
            return emitter;
        }

        // 组装评审内容
        String title = req.getTitle() != null ? req.getTitle() : "（无）";
        String cleanDesc = req.getDescription() != null && !req.getDescription().isBlank()
                ? RichTextCleaner.toPlainText(req.getDescription()) : "";
        String content = "标题：" + title;
        if (!cleanDesc.isEmpty()) content += "\n\n描述：\n" + cleanDesc;

        // §14.6 输入截断 8000 字符
        if (content.length() > 8000) {
            content = content.substring(0, 8000) + "\n\n（需求过长，本次仅评审前部）";
        }

        // contentHash 幂等复用（force=true 或有补充指令时跳过）
        String instruction = body.getInstruction();
        boolean forceReview = (body.getForce() != null && body.getForce())
                || (instruction != null && !instruction.isBlank());
        String contentHash = sha1(title + "|" + cleanDesc);
        if (!forceReview) {
            AiRequirementReview cached = checkReuse(requirementId, contentHash);
            if (cached != null) {
                runAsync(() -> {
                    Map<String, Object> payload = buildReviewPayload(cached);
                    payload.put("reused", true);
                    send(emitter, "reused", JSON.toJSONString(payload));
                    safeComplete(emitter);
                }, emitter);
                return emitter;
            }
        }

        final String reviewContent = content;

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            // 异步线程 ThreadLocal 丢失：恢复项目上下文（ModelManager 解析项目档案依赖它）
            ProjectContextHolder.setProjectId(projectId);
            java.util.concurrent.ScheduledFuture<?> hb = HEARTBEAT.scheduleAtFixedRate(
                    () -> send(emitter, "ping", "{}"), 15, 15, java.util.concurrent.TimeUnit.SECONDS);
            try {
                // 创建评审记录（提前创建以获取 reviewId，续传/停止都靠它）
                AiRequirementReview review = new AiRequirementReview();
                review.setProjectId(projectId);
                review.setRequirementId(requirementId);
                review.setContentHash(contentHash);
                review.setInstruction(instruction);
                review.setCreateUserId(userId);
                review.setCreateTime(new Date());
                review.setStatus("RUNNING");
                reviewMapper.insert(review);
                Long reviewId = review.getId();
                // 下发 record 事件（前端停止/重连都靠 reviewId）
                send(emitter, "record", JSON.toJSONString(Map.of("reviewId", reviewId)));
                // 创建 AiStreamBuffer（续传回放用）
                com.mokatest.platform.demos.ai.stream.AiStreamBuffer buffer = bufferRegistry.create(BUFFER_KEY + reviewId);
                doReview(projectId, userId, requirementId, reviewContent, emitter, contentHash,
                        instruction != null && instruction.length() > 500
                                ? instruction.substring(0, 500) : instruction,
                        reviewId, buffer);
            } finally {
                hb.cancel(false);
                ProjectContextHolder.clear();
            }
        });
        return emitter;
    }

    /**
     * 评审主流程（流式并行 + 专家级检查点 + buffer 回放）
     */
    private void doReview(Integer projectId, String userId,
                          Long requirementId, String content, SseEmitter emitter, String contentHash,
                          String instruction, Long reviewId,
                          com.mokatest.platform.demos.ai.stream.AiStreamBuffer buffer) {
        ActiveReview ar = new ActiveReview();
        ar.createUserId = userId;
        ar.reviewId = reviewId;
        ar.emitter = emitter;
        for (ParallelReviewService.ExpertDef def : ParallelReviewService.EXPERTS) {
            ar.expertStatus.put(def.key(), "PENDING");
        }
        ACTIVE.put(requirementId, ar);
        // 专家级状态机初始 JSON
        JSONObject expertResults = new JSONObject();
        for (ParallelReviewService.ExpertDef def : ParallelReviewService.EXPERTS) {
            JSONObject er = new JSONObject();
            er.put("status", "PENDING");
            expertResults.put(def.key(), er);
        }
        updateExpertResults(reviewId, expertResults);
        try {
            // 阶段一：4 专家流式并行
            Map<String, String> fullTexts = new ConcurrentHashMap<>();
            Map<String, String> failures = new ConcurrentHashMap<>();
            List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();
            for (ParallelReviewService.ExpertDef def : ParallelReviewService.EXPERTS) {
                ar.expertStatus.put(def.key(), "RUNNING");
                expertResults.getJSONObject(def.key()).put("status", "RUNNING");
                updateExpertResults(reviewId, expertResults);
                send(emitter, "expert_start", expertEvent(def.key()), buffer);
                futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                    // 并行专家跑在独立线程，需恢复项目上下文（ModelManager 依赖 ThreadLocal）
                    ProjectContextHolder.setProjectId(projectId);
                    try {
                        String feedback = (instruction != null && !instruction.isBlank())
                                ? "## 用户补充指令（最高优先级，如与本检查项冲突以本指令为准）\n" + instruction
                                : "";
                        fullTexts.put(def.key(), runExpertStreaming(def, content, feedback, emitter, buffer));
                        ar.expertStatus.put(def.key(), "DONE");
                        expertResults.getJSONObject(def.key()).put("status", "DONE");
                        send(emitter, "expert_done", expertEvent(def.key()), buffer);
                    } catch (Exception e) {
                        log.warn("评审专家 {} 调用失败: {}", def.key(), e.getMessage());
                        failures.put(def.key(), e.getMessage());
                        ar.expertStatus.put(def.key(), "FAILED");
                        expertResults.getJSONObject(def.key()).put("status", "FAILED");
                    } finally {
                        ProjectContextHolder.clear();
                    }
                }));
            }
            java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
            // 检查是否被取消（用户点了停止）
            if (buffer != null && buffer.isCancelRequested()) {
                persistExpertResults(reviewId, expertResults);
                List<String> completed = new ArrayList<>();
                for (ParallelReviewService.ExpertDef def : ParallelReviewService.EXPERTS) {
                    if ("DONE".equals(ar.expertStatus.get(def.key()))) completed.add(def.key());
                }
                send(emitter, "stopped", JSON.toJSONString(Map.of("completedExperts", completed)), buffer);
                updateReviewStatus(reviewId, "STOPPED", expertResults);
                safeComplete(emitter);
                return;
            }
            if (!failures.isEmpty()) {
                persistExpertResults(reviewId, expertResults);
                throw new BusinessException("评审专家调用失败：" + failures);
            }

            // 阶段二：逐专家解析+校验，失败单独流式重试一次（带反馈）
            Map<String, ExpertReviewResult> finalResults = new LinkedHashMap<>();
            for (ParallelReviewService.ExpertDef def : ParallelReviewService.EXPERTS) {
                // 被取消时提前退出
                if (buffer != null && buffer.isCancelRequested()) break;
                ExpertReviewResult r = null;
                String invalidReason;
                try {
                    r = parseExpertResult(fullTexts.get(def.key()));
                    invalidReason = validateExpert(def, r, content);
                } catch (Exception parseError) {
                    invalidReason = "输出解析失败：" + parseError.getMessage();
                }
                if (invalidReason != null) {
                    ar.expertStatus.put(def.key(), "RETRYING");
                    send(emitter, "expert_retry", expertEvent(def.key()), buffer);
                    log.warn("评审专家 {} 输出校验失败，重试一次: {}", def.key(), invalidReason);
                    String retryText = runExpertStreaming(def, content,
                            "## 上次输出校验失败，请修正以下问题后重新完整输出你负责的检查项：\n" + invalidReason, emitter, buffer);
                    try {
                        r = parseExpertResult(retryText);
                        invalidReason = validateExpert(def, r, content);
                    } catch (Exception parseError) {
                        invalidReason = "输出解析失败：" + parseError.getMessage();
                    }
                    if (invalidReason != null) {
                        ar.expertStatus.put(def.key(), "FAILED");
                        expertResults.getJSONObject(def.key()).put("status", "FAILED");
                        persistExpertResults(reviewId, expertResults);
                        throw new BusinessException("评审专家「" + def.label() + "」输出校验失败：" + invalidReason);
                    }
                    ar.expertStatus.put(def.key(), "DONE");
                    send(emitter, "expert_done", expertEvent(def.key()), buffer);
                }
                finalResults.put(def.key(), r);
            }
            // 被取消：部分聚合
            if (buffer != null && buffer.isCancelRequested()) {
                persistExpertResults(reviewId, expertResults);
                List<String> completed = new ArrayList<>(finalResults.keySet());
                send(emitter, "stopped", JSON.toJSONString(Map.of("completedExperts", completed)), buffer);
                updateReviewStatus(reviewId, "STOPPED", expertResults);
                safeComplete(emitter);
                return;
            }

            // 聚合
            ar.aggregating = true;
            send(emitter, "aggregating", "{}", buffer);
            String combinedJson = combineExpertResults(finalResults);
            ReviewScoreResult result = validateAndScore(combinedJson, content);
            if (!result.isValid()) {
                updateReviewFull(reviewId, null, combinedJson, expertResults, "FAILED");
                send(emitter, "error", "评审结果校验失败：" + result.getFeedback(), buffer);
                safeComplete(emitter);
                return;
            }
            result.setSummary(buildSummary(result, JSON.parseArray(result.getChecklistJson())));
            updateReviewFull(reviewId, result, combinedJson, expertResults, "SUCCESS");
            // 更新需求评审状态
            if (requirementId != null) {
                Requirement reqUpdate = new Requirement();
                reqUpdate.setId(requirementId.intValue());
                reqUpdate.setAiReviewStatus("REVIEWED");
                reqUpdate.setAiReviewScore(result.getOverall());
                reqUpdate.setAiReviewTime(new Date());
                requirementMapper.updateById(reqUpdate);
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("scores", JSON.parseObject(result.getScoresJson()));
            payload.put("overall", result.getOverall());
            payload.put("risks", JSON.parseArray(result.getRisksJson()));
            payload.put("summary", result.getSummary());
            payload.put("checklist", JSON.parseArray(result.getChecklistJson()));
            payload.put("confidence", result.getConfidence());
            payload.put("experts", expertSummaries(finalResults));
            if (buffer != null) buffer.finish(JSON.toJSONString(payload));
            send(emitter, "done", JSON.toJSONString(payload), null);
            safeComplete(emitter);
        } catch (Exception e) {
            log.error("评审失败", e);
            persistExpertResults(reviewId, expertResults);
            updateReviewStatus(reviewId, "FAILED", expertResults);
            if (buffer != null) buffer.fail(e.getMessage() != null ? e.getMessage() : "评审失败");
            sendError(emitter, "评审失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        } finally {
            ACTIVE.remove(requirementId);
        }
    }

    /** SSE 专家事件载荷：{expert, label} */
    private String expertEvent(String key) {
        ParallelReviewService.ExpertDef def = ParallelReviewService.expertDef(key);
        return JSON.toJSONString(Map.of("expert", def.key(), "label", def.label()));
    }

    /** 单专家流式执行：SSE 推 expert_delta，返回完整输出文本（阻塞至该专家完成） */
    private String runExpertStreaming(ParallelReviewService.ExpertDef def, String content,
                                      String feedback, SseEmitter emitter,
                                      com.mokatest.platform.demos.ai.stream.AiStreamBuffer buffer) throws Exception {
        ReviewExpert expert = parallelReviewService.createExpert();
        StringBuilder buf = new StringBuilder();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        TokenStream ts = expert.review(content, def.renderSpec(), feedback == null ? "" : feedback);
        ts.onPartialResponse(chunk -> {
                    if (chunk == null || chunk.isEmpty()) return;
                    buf.append(chunk);
                    String data = JSON.toJSONString(Map.of("expert", def.key(), "chunk", chunk));
                    send(emitter, "expert_delta", data, buffer);
                })
                .onCompleteResponse(r -> latch.countDown())
                .onError(t -> {
                    error.set(t);
                    latch.countDown();
                })
                .start();
        latch.await();
        if (error.get() != null) {
            throw new BusinessException("模型调用失败：" + error.get().getMessage());
        }
        return buf.toString();
    }

    /** 专家输出解析：抽 JSON → 手工映射为 ExpertReviewResult（answer 枚举非法时按 null 交给校验拦截） */
    private ExpertReviewResult parseExpertResult(String text) {
        String json = ReviewChecklist.extractJson(text);
        JSONObject obj;
        try {
            obj = JSON.parseObject(json);
        } catch (Exception e) {
            throw new BusinessException("输出不是合法 JSON");
        }
        if (obj == null) {
            throw new BusinessException("输出为空");
        }
        List<com.mokatest.platform.demos.ai.domain.ReviewResult.ChecklistEntry> entries = new ArrayList<>();
        JSONArray checklist = obj.getJSONArray("checklist");
        if (checklist != null) {
            for (int i = 0; i < checklist.size(); i++) {
                JSONObject item = checklist.getJSONObject(i);
                if (item == null) continue;
                com.mokatest.platform.demos.ai.domain.ReviewResult.Answer answer = null;
                String answerStr = item.getString("answer");
                if (answerStr != null && Set.of("YES", "PARTIAL", "NO").contains(answerStr)) {
                    answer = com.mokatest.platform.demos.ai.domain.ReviewResult.Answer.valueOf(answerStr);
                }
                entries.add(new com.mokatest.platform.demos.ai.domain.ReviewResult.ChecklistEntry(
                        item.getString("id"), answer, item.getString("quote")));
            }
        }
        List<com.mokatest.platform.demos.ai.domain.ReviewResult.Risk> risks = new ArrayList<>();
        JSONArray risksArr = obj.getJSONArray("risks");
        if (risksArr != null) {
            for (int i = 0; i < risksArr.size(); i++) {
                JSONObject item = risksArr.getJSONObject(i);
                if (item == null) continue;
                risks.add(new com.mokatest.platform.demos.ai.domain.ReviewResult.Risk(
                        item.getString("dimension"), item.getString("level"), item.getString("desc"),
                        item.getString("suggestion"), item.getString("quote"), item.getString("checklistId")));
            }
        }
        return new ExpertReviewResult(entries, risks);
    }

    /**
     * 单专家输出校验：覆盖度（负责的检查项 id 齐全）+ answer 合法 + quote 命中率（幻觉拦截）。
     *
     * @return 校验失败原因（作为重试反馈）；null = 通过
     */
    private String validateExpert(ParallelReviewService.ExpertDef def, ExpertReviewResult r, String content) {
        if (r == null || r.checklist() == null || r.checklist().isEmpty()) {
            return "未返回任何检查项结果";
        }
        List<String> expected = def.checklistIds();
        Set<String> returned = new HashSet<>();
        String normalizedText = normalize(content);
        int quoteChecked = 0;
        int quoteValid = 0;
        for (ReviewResultEntry entry : ReviewResultEntry.from(r)) {
            returned.add(entry.id());
            if (entry.answer() != null && !Set.of("YES", "PARTIAL", "NO").contains(entry.answer())) {
                return "第 " + entry.id() + " 项 answer 值\"" + entry.answer() + "\"非法（允许 YES/PARTIAL/NO）";
            }
            if (entry.quote() != null && !entry.quote().isEmpty()) {
                quoteChecked++;
                if (normalizedText.contains(normalize(entry.quote()))) quoteValid++;
            }
        }
        List<String> missing = new ArrayList<>(expected);
        missing.removeAll(returned);
        if (!missing.isEmpty()) {
            return "checklist 缺少 id=[" + String.join(", ", missing)
                    + "]，必须完整覆盖你负责的 " + expected.size() + " 项";
        }
        // 越界检查项（答了别的专家的项）：不算失败，聚合时会被过滤
        if (quoteChecked >= 4 && quoteValid * 2 < quoteChecked) {
            return "证据引用命中率过低（" + quoteValid + "/" + quoteChecked
                    + "）。quote 必须是需求原文的连续子串（≤60字），禁止转述或编造；找不到原文依据的项 quote 置空字符串。";
        }
        return null;
    }

    /** ExpertReviewResult 条目扁平化（record 嵌套类型转发用） */
    private record ReviewResultEntry(String id, String answer, String quote) {
        static List<ReviewResultEntry> from(ExpertReviewResult r) {
            List<ReviewResultEntry> list = new ArrayList<>();
            for (com.mokatest.platform.demos.ai.domain.ReviewResult.ChecklistEntry e : r.checklist()) {
                list.add(new ReviewResultEntry(e.id(), e.answer() == null ? null : e.answer().name(), e.quote()));
            }
            return list;
        }
    }

    /** 聚合 4 专家输出为契约 v2 JSON 文本（checklist 30 项合并 + risks 去重合并），复用 validateAndScore */
    private String combineExpertResults(Map<String, ExpertReviewResult> results) {
        JSONArray checklist = new JSONArray();
        JSONArray risks = new JSONArray();
        Set<String> riskKeys = new HashSet<>();
        for (ParallelReviewService.ExpertDef def : ParallelReviewService.EXPERTS) {
            ExpertReviewResult r = results.get(def.key());
            if (r == null) continue;
            Set<String> allowed = new HashSet<>(def.checklistIds());
            for (com.mokatest.platform.demos.ai.domain.ReviewResult.ChecklistEntry e : r.checklist()) {
                if (e.id() == null || !allowed.contains(e.id())) continue; // 过滤越界项
                JSONObject item = new JSONObject();
                item.put("id", e.id());
                item.put("answer", e.answer() == null ? "NO" : e.answer().name());
                item.put("quote", e.quote() == null ? "" : e.quote());
                checklist.add(item);
            }
            if (r.risks() != null) {
                for (com.mokatest.platform.demos.ai.domain.ReviewResult.Risk risk : r.risks()) {
                    // 去重键：维度+关联检查项+描述前 20 字（同维度近似风险只留一条）
                    String desc = risk.desc() == null ? "" : risk.desc();
                    String key = risk.dimension() + "|" + risk.checklistId() + "|"
                            + desc.substring(0, Math.min(20, desc.length()));
                    if (!riskKeys.add(key)) continue;
                    JSONObject item = new JSONObject();
                    item.put("dimension", risk.dimension());
                    item.put("level", risk.level());
                    item.put("desc", risk.desc());
                    item.put("suggestion", risk.suggestion());
                    item.put("quote", risk.quote());
                    item.put("checklistId", risk.checklistId());
                    risks.add(item);
                }
            }
        }
        JSONObject combined = new JSONObject();
        combined.put("checklist", checklist);
        combined.put("risks", risks);
        combined.put("summary", "");
        return combined.toJSONString();
    }

    /** done 事件附带的专家摘要（前端「按专家查看」用） */
    private List<Map<String, Object>> expertSummaries(Map<String, ExpertReviewResult> results) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ParallelReviewService.ExpertDef def : ParallelReviewService.EXPERTS) {
            ExpertReviewResult r = results.get(def.key());
            Map<String, Object> item = new HashMap<>();
            item.put("expert", def.key());
            item.put("label", def.label());
            item.put("itemCount", r == null || r.checklist() == null ? 0 : r.checklist().size());
            item.put("riskCount", r == null || r.risks() == null ? 0 : r.risks().size());
            list.add(item);
        }
        return list;
    }

    /** 总评：服务端模板生成（确定性，零额外模型调用） */
    private String buildSummary(ReviewScoreResult result, JSONArray checklist) {
        JSONObject scores = JSON.parseObject(result.getScoresJson());
        String bestDim = null;
        String worstDim = null;
        int best = -1;
        int worst = 101;
        for (Map.Entry<String, String> e : ReviewChecklist.DIM_LABELS.entrySet()) {
            int s = scores.getInteger(e.getKey()) == null ? 0 : scores.getInteger(e.getKey());
            if (s > best) { best = s; bestDim = e.getKey(); }
            if (s < worst) { worst = s; worstDim = e.getKey(); }
        }
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < checklist.size() && missing.size() < 5; i++) {
            JSONObject item = checklist.getJSONObject(i);
            if ("NO".equals(item.getString("answer"))) {
                missing.add(ReviewChecklist.ITEM_TEXTS.get(item.getString("id")));
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("本次评审总分 ").append(result.getOverall()).append("/100。");
        if (bestDim != null && worstDim != null) {
            sb.append(ReviewChecklist.DIM_LABELS.get(worstDim)).append("（").append(worst).append("分）相对薄弱，")
              .append(ReviewChecklist.DIM_LABELS.get(bestDim)).append("（").append(best).append("分）较好。");
        }
        if (!missing.isEmpty()) {
            sb.append("主要缺失：").append(String.join("；", missing)).append("。");
            sb.append("建议优先补充上述内容后重新评审。");
        } else {
            sb.append("各维度覆盖良好，可进入用例设计阶段。");
        }
        return sb.toString();
    }

    // ==================== 校验 + 算分管线（§14.5） ====================

    private ReviewScoreResult validateAndScore(String json, String originalText) {
        ReviewScoreResult result = new ReviewScoreResult();
        List<String> errors = new ArrayList<>();

        // 空正文兜底：fastjson 对空串返回 null 而不抛异常，须显式判空给出人话反馈
        if (json == null || json.isBlank()) {
            result.setValid(false);
            result.setFeedback("模型未返回正文内容（可能 token 上限被思考过程占满或输出被截断），请增大 maxTokens 后重试");
            return result;
        }

        JSONObject obj;
        JSONArray checklist;
        try {
            obj = JSON.parseObject(json);
            if (obj == null) {
                throw new IllegalArgumentException("模型输出为空或不是 JSON 对象");
            }
            checklist = obj.getJSONArray("checklist");
        } catch (Exception e) {
            result.setValid(false);
            result.setFeedback("JSON 解析失败：" + e.getMessage());
            return result;
        }

        // 结构校验
        if (checklist == null) {
            errors.add("缺少 checklist 字段");
        } else {
            if (checklist.size() != 30) {
                errors.add("checklist 必须包含 30 项，当前 " + checklist.size() + " 项");
            }
            Set<String> returnedIds = new HashSet<>();
            for (int i = 0; i < checklist.size(); i++) {
                JSONObject item = checklist.getJSONObject(i);
                String id = item.getString("id");
                if (id != null) returnedIds.add(id);
                String answer = item.getString("answer");
                if (answer != null && !"YES".equals(answer) && !"PARTIAL".equals(answer) && !"NO".equals(answer)) {
                    errors.add("第 " + id + " 项 answer 值\"" + answer + "\"非法（允许 YES/PARTIAL/NO）");
                }
            }
            List<String> missing = new ArrayList<>();
            for (String expected : ReviewChecklist.CHECKLIST_IDS) {
                if (!returnedIds.contains(expected)) missing.add(expected);
            }
            if (!missing.isEmpty()) {
                errors.add("checklist 缺少 id=[" + String.join(", ", missing) + "]");
            }
        }

        if (!errors.isEmpty()) {
            result.setValid(false);
            result.setFeedback(String.join("；", errors));
            return result;
        }

        // quote 子串校验
        String normalizedText = normalize(originalText);
        int quoteChecked = 0;
        int quoteValid = 0;
        for (int i = 0; i < checklist.size(); i++) {
            JSONObject item = checklist.getJSONObject(i);
            String quote = item.getString("quote");
            if (quote != null && !quote.isEmpty()) {
                quoteChecked++;
                if (normalizedText.contains(normalize(quote))) {
                    quoteValid++;
                } else {
                    item.put("evidenceInvalid", true);
                }
            }
        }
        // 幻觉拦截：引用命中率低于一半说明模型在编造依据，判失败带反馈重试（而非产出不可信的评审）
        if (quoteChecked >= 4 && quoteValid * 2 < quoteChecked) {
            result.setValid(false);
            result.setFeedback("证据引用命中率过低（" + quoteValid + "/" + quoteChecked
                    + "）。quote 必须是需求原文的连续子串（≤60字），禁止转述或编造；找不到原文依据的项 quote 置空字符串。请逐条核对原文后重新完整输出。");
            return result;
        }

        // risks 枚举校验 + quote 校验
        JSONArray risks = obj.getJSONArray("risks");
        JSONArray filteredRisks = new JSONArray();
        Set<String> validDims = Set.of("completeness", "clarity", "testability", "consistency", "security", "boundary");
        Set<String> validLevels = Set.of("HIGH", "MEDIUM", "LOW");
        if (risks != null) {
            for (int i = 0; i < risks.size(); i++) {
                JSONObject risk = risks.getJSONObject(i);
                String dim = risk.getString("dimension");
                String level = risk.getString("level");
                if (dim != null && validDims.contains(dim) && level != null && validLevels.contains(level)) {
                    String rQuote = risk.getString("quote");
                    if (rQuote != null && !rQuote.isEmpty() && !normalizedText.contains(normalize(rQuote))) {
                        risk.put("evidenceInvalid", true);
                    }
                    // checklistId 合法性：不在 30 项内的置空（视为检查表之外的深层洞察）
                    String cid = risk.getString("checklistId");
                    if (cid != null && !cid.isEmpty()
                            && !ReviewChecklist.CHECKLIST_IDS.contains(cid)) {
                        risk.put("checklistId", "");
                    }
                    filteredRisks.add(risk);
                }
            }
        }

        // 服务端确定性算分
        Map<String, List<Integer>> dimScores = new LinkedHashMap<>();
        for (String dim : List.of("completeness", "clarity", "testability", "consistency", "security", "boundary")) {
            dimScores.put(dim, new ArrayList<>());
        }
        for (int i = 0; i < checklist.size(); i++) {
            JSONObject item = checklist.getJSONObject(i);
            String id = item.getString("id");
            String answer = item.getString("answer");
            String dim = ReviewChecklist.dimOfId(id);
            if (dim != null) {
                int score = "YES".equals(answer) ? 100 : "PARTIAL".equals(answer) ? 50 : 0;
                dimScores.get(dim).add(score);
            }
        }

        JSONObject scores = new JSONObject();
        int overallSum = 0;
        for (Map.Entry<String, List<Integer>> entry : dimScores.entrySet()) {
            List<Integer> vals = entry.getValue();
            if (!vals.isEmpty()) {
                int avg = (int) vals.stream().mapToInt(Integer::intValue).average().orElse(0);
                scores.put(entry.getKey(), avg);
                overallSum += avg;
            }
        }
        int overall = overallSum / 6;

        double passRate = quoteChecked > 0 ? (double) quoteValid / quoteChecked : 1.0;
        String confidence = passRate >= 0.9 ? "HIGH" : passRate >= 0.7 ? "MEDIUM" : "LOW";

        result.setValid(true);
        result.setScoresJson(scores.toJSONString());
        result.setOverall(overall);
        result.setConfidence(confidence);
        result.setChecklistJson(checklist.toJSONString());
        result.setRisksJson(filteredRisks.toJSONString());
        result.setSummary(obj.getString("summary"));
        return result;
    }

    // ==================== 落库 ====================

    private void persistReview(Integer projectId, String userId, Long requirementId,
                               ReviewScoreResult result, String rawText, String contentHash,
                               String instruction) {
        try {
            AiRequirementReview review = new AiRequirementReview();
            review.setProjectId(projectId);
            review.setRequirementId(requirementId);
            review.setContentHash(contentHash);
            review.setRawText(rawText);
            review.setInstruction(instruction);
            review.setCreateUserId(userId);
            review.setCreateTime(new Date());

            if (result != null && result.isValid()) {
                review.setScoresJson(result.getScoresJson());
                review.setOverallScore(result.getOverall());
                review.setRisksJson(result.getRisksJson());
                review.setChecklistJson(result.getChecklistJson());
                review.setConfidence(result.getConfidence());
                review.setSummary(result.getSummary());
                review.setStatus("SUCCESS");
            } else {
                review.setStatus("FAILED");
            }
            reviewMapper.insert(review);

            if (result != null && result.isValid() && requirementId != null) {
                Requirement reqUpdate = new Requirement();
                reqUpdate.setId(requirementId.intValue());
                reqUpdate.setAiReviewStatus("REVIEWED");
                reqUpdate.setAiReviewScore(result.getOverall());
                reqUpdate.setAiReviewTime(new Date());
                requirementMapper.updateById(reqUpdate);
            }
        } catch (Exception e) {
            log.warn("评审结果落库失败: {}", e.getMessage());
        }
    }

    // ==================== AI 补写（评审闭环：就地补需求） ====================

    @Data
    public static class PatchRequest {
        private Long requirementId;
        /** 缺失检查项 id 列表（单项补写传 1 个；整体补全传全部缺失项） */
        private List<String> itemIds;
    }

    /**
     * AI 补写：根据需求全文 + 缺失检查项，生成可直接追加到需求文档的补充段落。
     * 同步小调用（maxTokens 800），scene=REQUIREMENT_PATCH 计量。
     */
    @PostMapping("/patch")
    public SaResult patch(@RequestBody PatchRequest body) {
        Integer projectId = ProjectContextHolder.getProjectId();
        if (body.getRequirementId() == null) throw new BusinessException("请指定需求ID");

        Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", body.getRequirementId())
                .eq("project_id", projectId)
                .select("id", "title", "description"));
        if (req == null) throw new BusinessException("需求不存在");

        String cleanDesc = req.getDescription() != null ? RichTextCleaner.toPlainText(req.getDescription()) : "";
        String requirementText = "标题：" + (req.getTitle() != null ? req.getTitle() : "（无）")
                + (cleanDesc.isBlank() ? "" : "\n\n描述：\n" + cleanDesc);
        if (requirementText.length() > 8000) {
            requirementText = requirementText.substring(0, 8000);
        }

        List<String> missing = new ArrayList<>();
        if (body.getItemIds() != null) {
            for (String id : body.getItemIds()) {
                String text = ReviewChecklist.ITEM_TEXTS.get(id);
                if (text != null) missing.add(id + "：" + text);
            }
        }
        if (missing.isEmpty()) throw new BusinessException("请指定要补写的检查项");

        String system = "你是需求文档补写助手。根据给定需求及其缺失的检查项，撰写可直接追加到需求文档中的补充内容。"
                + "要求：只输出补充内容本身（纯文本，按检查项分段，每段以【检查项】开头），"
                + "内容要具体可执行（给出规则、数值、异常处理），不要解释、不要复述需求、不要 markdown 标题。";
        String user = "## 需求内容\n" + requirementText
                + "\n\n## 需要补充的缺失项\n" + String.join("\n", missing);

        ChatModel chatModel = modelManager.getChatModel();
        ChatRequest chatReq = ChatRequest.builder()
                .messages(SystemMessage.from(system), UserMessage.from(user))
                .build();
        ChatResponse resp = com.mokatest.platform.demos.ai.config.AiUsageContext.withScene(
                "REQUIREMENT_PATCH", projectId, () -> chatModel.chat(chatReq));
        String resultText = resp.aiMessage().text();
        return SaResult.ok().setData(Map.of("text", resultText != null ? resultText : ""));
    }

    // ==================== 查询接口 ====================

    @GetMapping("/list")
    public SaResult list(@RequestParam(required = false) Long requirementId,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "20") Integer pageSize) {
        Integer projectId = ProjectContextHolder.getProjectId();
        QueryWrapper<AiRequirementReview> qw = new QueryWrapper<AiRequirementReview>()
                .eq("project_id", projectId)
                .orderByDesc("id")
                .select("id", "requirement_id", "overall_score", "summary",
                        "confidence", "status", "create_user_id", "create_time", "instruction");
        if (requirementId != null) qw.eq("requirement_id", requirementId);
        IPage<AiRequirementReview> pageParam = new Page<>(page, pageSize);
        return SaResult.ok().setData(reviewMapper.selectPage(pageParam, qw));
    }

    @GetMapping("/latest")
    public SaResult latest(@RequestParam(required = false) Long requirementId) {
        Integer projectId = ProjectContextHolder.getProjectId();
        QueryWrapper<AiRequirementReview> qw = new QueryWrapper<AiRequirementReview>()
                .eq("project_id", projectId)
                .eq("status", "SUCCESS")
                .orderByDesc("id")
                .last("limit 1");
        if (requirementId != null) qw.eq("requirement_id", requirementId);
        AiRequirementReview review = reviewMapper.selectOne(qw);
        return SaResult.ok().setData(review);
    }

    @GetMapping("/{id}")
    public SaResult getById(@PathVariable Long id) {
        Integer projectId = ProjectContextHolder.getProjectId();
        AiRequirementReview review = reviewMapper.selectOne(new QueryWrapper<AiRequirementReview>()
                .eq("id", id)
                .eq("project_id", projectId));
        if (review == null) throw new BusinessException("评审记录不存在");
        return SaResult.ok().setData(review);
    }

    @DeleteMapping("/{id}")
    public SaResult delete(@PathVariable Long id) {
        Integer projectId = ProjectContextHolder.getProjectId();
        String userId = StpUtil.getLoginIdAsString();
        AiRequirementReview review = reviewMapper.selectOne(new QueryWrapper<AiRequirementReview>()
                .eq("id", id)
                .eq("project_id", projectId));
        if (review == null) throw new BusinessException("评审记录不存在");
        if (!userId.equals(review.getCreateUserId())
                && !superAdminChecker.isSuperAdmin(userId)) {
            throw new BusinessException("仅评审创建人或超管可删除");
        }
        reviewMapper.deleteById(id);
        return SaResult.ok("删除成功");
    }

    // ==================== 活跃评审查询 ====================

    @GetMapping("/active")
    public SaResult active(@RequestParam Long requirementId) {
        ActiveReview ar = ACTIVE.get(requirementId);
        if (ar == null) return SaResult.ok().setData(Map.of("active", false));
        Map<String, Object> data = new HashMap<>();
        data.put("active", true);
        data.put("expertStatus", ar.expertStatus);
        data.put("aggregating", ar.aggregating);
        data.put("createUserId", ar.createUserId);
        return SaResult.ok().setData(data);
    }

    // ==================== 工具方法 ====================

    private AiRequirementReview checkReuse(Long requirementId, String contentHash) {
        Date since = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        return reviewMapper.selectOne(new QueryWrapper<AiRequirementReview>()
                .eq("requirement_id", requirementId)
                .eq("content_hash", contentHash)
                .eq("status", "SUCCESS")
                .gt("create_time", since)
                .orderByDesc("id")
                .last("limit 1"));
    }

    private Map<String, Object> buildReviewPayload(AiRequirementReview review) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("scores", review.getScoresJson() != null ? JSON.parseObject(review.getScoresJson()) : null);
        payload.put("overall", review.getOverallScore());
        payload.put("risks", review.getRisksJson() != null ? JSON.parseArray(review.getRisksJson()) : null);
        payload.put("summary", review.getSummary());
        payload.put("checklist", review.getChecklistJson() != null ? JSON.parseArray(review.getChecklistJson()) : null);
        payload.put("confidence", review.getConfidence());
        return payload;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", "");
    }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("SSE complete 失败（连接已回收）: {}", e.getMessage());
        }
    }

    /** SSE 推送 + buffer 写入（buffer 为 null 时不写） */
    private void send(SseEmitter emitter, String event, String data,
                      com.mokatest.platform.demos.ai.stream.AiStreamBuffer buffer) {
        if (buffer != null) {
            buffer.append(event + "\u0001" + data + "\u0002");
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE 推送失败（客户端断开）: {}", e.getMessage());
        }
    }

    /** 兼容旧调用：不带 buffer 的 send */
    private void send(SseEmitter emitter, String event, String data) {
        send(emitter, event, data, null);
    }

    /** 专家级状态机落库 */
    private void updateExpertResults(Long reviewId, JSONObject expertResults) {
        try {
            AiRequirementReview update = new AiRequirementReview();
            update.setId(reviewId);
            update.setExpertResults(expertResults.toJSONString());
            reviewMapper.updateById(update);
        } catch (Exception e) {
            log.warn("专家结果落库失败: {}", e.getMessage());
        }
    }

    private void persistExpertResults(Long reviewId, JSONObject expertResults) {
        updateExpertResults(reviewId, expertResults);
    }

    /** 更新评审状态 */
    private void updateReviewStatus(Long reviewId, String status, JSONObject expertResults) {
        try {
            AiRequirementReview update = new AiRequirementReview();
            update.setId(reviewId);
            update.setStatus(status);
            update.setExpertResults(expertResults.toJSONString());
            reviewMapper.updateById(update);
        } catch (Exception e) {
            log.warn("评审状态更新失败: {}", e.getMessage());
        }
    }

    /** 评审完成后全量更新记录 */
    private void updateReviewFull(Long reviewId, ReviewScoreResult result, String rawText,
                                   JSONObject expertResults, String status) {
        try {
            AiRequirementReview update = new AiRequirementReview();
            update.setId(reviewId);
            update.setStatus(status);
            update.setRawText(rawText);
            update.setExpertResults(expertResults.toJSONString());
            if (result != null && result.isValid()) {
                update.setScoresJson(result.getScoresJson());
                update.setOverallScore(result.getOverall());
                update.setRisksJson(result.getRisksJson());
                update.setChecklistJson(result.getChecklistJson());
                update.setConfidence(result.getConfidence());
                update.setSummary(result.getSummary());
            }
            reviewMapper.updateById(update);
        } catch (Exception e) {
            log.warn("评审结果落库失败: {}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String msg) {
        send(emitter, "error", msg);
        safeComplete(emitter);
    }

    private void runAsync(Runnable task, SseEmitter emitter) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                sendError(emitter, e.getMessage() != null ? e.getMessage() : "评审失败");
            }
        });
    }

    // ==================== 续传接口 ====================

    /** 停止评审：已完成专家结论保留，在途专家取消 */
    @PostMapping("/stop")
    public SaResult stop(@RequestParam Long reviewId) {
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer userId = StpUtil.getLoginIdAsInt();
        // IDOR 防护：校验评审记录归属
        AiRequirementReview review = reviewMapper.selectById(reviewId);
        if (review == null || !review.getProjectId().equals(projectId)) {
            return SaResult.error("评审记录不存在或无权访问");
        }
        com.mokatest.platform.demos.ai.stream.AiStreamBuffer buffer = bufferRegistry.get(BUFFER_KEY + reviewId);
        if (buffer != null) {
            buffer.requestCancel();
        }
        return SaResult.ok("已请求停止");
    }

    /** 重连评审流：回放已完成专家 + 接续在途专家 */
    @GetMapping(value = "/attach", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter attach(@RequestParam Long reviewId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(() -> { safeComplete(emitter); });
        String bufferKey = BUFFER_KEY + reviewId;
        com.mokatest.platform.demos.ai.stream.AiStreamBuffer buffer = bufferRegistry.get(bufferKey);
        if (buffer == null) {
            // buffer 不在（进程重启 / TTL 过期），从 DB 恢复
            AiRequirementReview review = reviewMapper.selectById(reviewId);
            if (review == null) {
                runAsync(() -> sendError(emitter, "评审记录不存在"), emitter);
                return emitter;
            }
            // 从 DB 恢复专家状态 + 已完成结论
            String restoreJson = buildRestoreFromDb(review);
            runAsync(() -> {
                send(emitter, "restore", restoreJson);
                safeComplete(emitter);
            }, emitter);
            return emitter;
        }
        // buffer 存在：回放 + 接续
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            // 回放 buffer 中的全部事件
            String fullText = buffer.text();
            if (fullText != null && !fullText.isEmpty()) {
                // buffer 格式：event\u0001data\u0002event\u0001data\u0002...
                String[] events = fullText.split("\u0002");
                for (String evt : events) {
                    if (evt == null || evt.isEmpty()) continue;
                    int sep = evt.indexOf('\u0001');
                    if (sep > 0) {
                        String eventName = evt.substring(0, sep);
                        String eventData = evt.substring(sep + 1);
                        send(emitter, eventName, eventData);
                    }
                }
            }
            // 检查终态
            com.mokatest.platform.demos.ai.stream.AiStreamBuffer.State state = buffer.getState();
            if (state == com.mokatest.platform.demos.ai.stream.AiStreamBuffer.State.DONE) {
                send(emitter, "done", buffer.getResultJson() != null ? buffer.getResultJson() : "{}");
                safeComplete(emitter);
            } else if (state == com.mokatest.platform.demos.ai.stream.AiStreamBuffer.State.FAILED) {
                sendError(emitter, buffer.getErrorMsg() != null ? buffer.getErrorMsg() : "评审失败");
            } else if (state == com.mokatest.platform.demos.ai.stream.AiStreamBuffer.State.STOPPED) {
                send(emitter, "stopped", "{}");
                safeComplete(emitter);
            } else {
                // STREAMING：接续实时流（轮询 buffer 新增内容）
                int offset = fullText != null ? fullText.length() : 0;
                long deadline = System.currentTimeMillis() + 280_000;
                while (System.currentTimeMillis() < deadline) {
                    try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                    com.mokatest.platform.demos.ai.stream.AiStreamBuffer.State st = buffer.getState();
                    if (st != com.mokatest.platform.demos.ai.stream.AiStreamBuffer.State.STREAMING) {
                        // 推送新增内容
                        String newText = buffer.text();
                        if (newText != null && newText.length() > offset) {
                            String added = newText.substring(offset);
                            offset = newText.length();
                            for (String evt : added.split("\u0002")) {
                                if (evt == null || evt.isEmpty()) continue;
                                int sep = evt.indexOf('\u0001');
                                if (sep > 0) {
                                    send(emitter, evt.substring(0, sep), evt.substring(sep + 1));
                                }
                            }
                        }
                        // 推终态
                        if (st == com.mokatest.platform.demos.ai.stream.AiStreamBuffer.State.DONE) {
                            send(emitter, "done", buffer.getResultJson() != null ? buffer.getResultJson() : "{}");
                        } else if (st == com.mokatest.platform.demos.ai.stream.AiStreamBuffer.State.FAILED) {
                            send(emitter, "error", buffer.getErrorMsg() != null ? buffer.getErrorMsg() : "评审失败");
                        } else if (st == com.mokatest.platform.demos.ai.stream.AiStreamBuffer.State.STOPPED) {
                            send(emitter, "stopped", "{}");
                        }
                        safeComplete(emitter);
                        return;
                    }
                    // 推送新增内容
                    String newText = buffer.text();
                    if (newText != null && newText.length() > offset) {
                        String added = newText.substring(offset);
                        offset = newText.length();
                        for (String evt : added.split("\u0002")) {
                            if (evt == null || evt.isEmpty()) continue;
                            int sep = evt.indexOf('\u0001');
                            if (sep > 0) {
                                send(emitter, evt.substring(0, sep), evt.substring(sep + 1));
                            }
                        }
                    }
                }
                // 超时
                sendError(emitter, "重连超时，请刷新重试");
            }
        });
        return emitter;
    }

    /** 从 DB 构建回放恢复数据 */
    private String buildRestoreFromDb(AiRequirementReview review) {
        JSONObject restore = new JSONObject();
        restore.put("status", review.getStatus());
        restore.put("reviewId", review.getId());
        if (review.getExpertResults() != null) {
            try {
                restore.put("expertResults", JSON.parseObject(review.getExpertResults()));
            } catch (Exception ignored) {}
        }
        // 如果评审已完成，带上完整结论
        if ("SUCCESS".equals(review.getStatus())) {
            Map<String, Object> payload = buildReviewPayload(review);
            restore.put("result", payload);
        }
        return restore.toJSONString();
    }

    /** 续跑：仅重跑 PENDING/FAILED 专家 */
    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resume(@RequestParam Long reviewId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(() -> { sendError(emitter, "评审续跑超时"); });
        Integer projectId = ProjectContextHolder.getProjectId();
        AiRequirementReview review = reviewMapper.selectById(reviewId);
        if (review == null || !review.getProjectId().equals(projectId)) {
            runAsync(() -> sendError(emitter, "评审记录不存在或无权访问"), emitter);
            return emitter;
        }
        if (!"STOPPED".equals(review.getStatus()) && !"FAILED".equals(review.getStatus())) {
            runAsync(() -> sendError(emitter, "当前状态不支持续跑"), emitter);
            return emitter;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            // 异步线程 ThreadLocal 丢失：恢复项目上下文（ModelManager 解析项目档案依赖它）
            ProjectContextHolder.setProjectId(projectId);
            java.util.concurrent.ScheduledFuture<?> hb = HEARTBEAT.scheduleAtFixedRate(
                    () -> send(emitter, "ping", "{}"), 15, 15, java.util.concurrent.TimeUnit.SECONDS);
            try {
                // 恢复专家状态（单次赋值，lambda 安全）
                JSONObject expertResults = review.getExpertResults() != null
                        ? JSON.parseObject(review.getExpertResults()) : new JSONObject();
                // 初始化缺失的专家状态
                for (ParallelReviewService.ExpertDef def : ParallelReviewService.EXPERTS) {
                    if (!expertResults.containsKey(def.key())) {
                        expertResults.put(def.key(), new JSONObject().fluentPut("status", "PENDING"));
                    }
                }
                // 获取需求内容
                Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                        .eq("id", review.getRequirementId()).eq("project_id", projectId)
                        .select("id", "title", "description"));
                if (req == null) {
                    sendError(emitter, "需求不存在");
                    return;
                }
                String cleanDesc = req.getDescription() != null && !req.getDescription().isBlank()
                        ? RichTextCleaner.toPlainText(req.getDescription()) : "";
                String content = "标题：" + (req.getTitle() != null ? req.getTitle() : "（无）");
                if (!cleanDesc.isEmpty()) content += "\n\n描述：\n" + cleanDesc;
                if (content.length() > 8000) content = content.substring(0, 8000);
                final String reviewContent = content;
                String instruction = review.getInstruction();

                // 创建新 buffer
                com.mokatest.platform.demos.ai.stream.AiStreamBuffer buffer = bufferRegistry.create(BUFFER_KEY + reviewId);
                send(emitter, "record", JSON.toJSONString(Map.of("reviewId", reviewId)), buffer);

                // 仅重跑 PENDING/FAILED 专家
                Map<String, String> fullTexts = new ConcurrentHashMap<>();
                List<java.util.concurrent.CompletableFuture<Void>> futures = new ArrayList<>();
                for (ParallelReviewService.ExpertDef def : ParallelReviewService.EXPERTS) {
                    String expertStatus = expertResults.getJSONObject(def.key()) != null
                            ? expertResults.getJSONObject(def.key()).getString("status") : "PENDING";
                    if ("DONE".equals(expertStatus)) continue; // 跳过已完成
                    expertResults.getJSONObject(def.key()).put("status", "RUNNING");
                    send(emitter, "expert_start", expertEvent(def.key()), buffer);
                    futures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                        // 并行专家跑在独立线程，需恢复项目上下文（ModelManager 依赖 ThreadLocal）
                        ProjectContextHolder.setProjectId(projectId);
                        try {
                            String feedback = (instruction != null && !instruction.isBlank())
                                    ? "## 用户补充指令\n" + instruction : "";
                            fullTexts.put(def.key(), runExpertStreaming(def, reviewContent, feedback, emitter, buffer));
                            expertResults.getJSONObject(def.key()).put("status", "DONE");
                            send(emitter, "expert_done", expertEvent(def.key()), buffer);
                        } catch (Exception e) {
                            expertResults.getJSONObject(def.key()).put("status", "FAILED");
                        } finally {
                            ProjectContextHolder.clear();
                        }
                    }));
                }
                if (!futures.isEmpty()) {
                    java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0])).join();
                }
                persistExpertResults(reviewId, expertResults);

                // 聚合所有已完成专家
                Map<String, ExpertReviewResult> finalResults = new LinkedHashMap<>();
                for (ParallelReviewService.ExpertDef def : ParallelReviewService.EXPERTS) {
                    String expertStatus = expertResults.getJSONObject(def.key()) != null
                            ? expertResults.getJSONObject(def.key()).getString("status") : "PENDING";
                    if (!"DONE".equals(expertStatus)) continue;
                    // 对续跑的专家用新文本，对之前完成的用 rawText 中的
                    String text = fullTexts.getOrDefault(def.key(), "");
                    if (text.isEmpty() && review.getRawText() != null) {
                        // 尝试从 rawText 恢复（简化处理）
                        text = review.getRawText();
                    }
                    try {
                        ExpertReviewResult r = parseExpertResult(text);
                        finalResults.put(def.key(), r);
                    } catch (Exception ignored) {}
                }

                ar_aggregating(emitter, buffer);
                String combinedJson = combineExpertResults(finalResults);
                ReviewScoreResult result = validateAndScore(combinedJson, reviewContent);
                boolean incomplete = finalResults.size() < ParallelReviewService.EXPERTS.size();
                if (incomplete && result.isValid()) {
                    result.setSummary("评审不完整（" + finalResults.size() + "/" + ParallelReviewService.EXPERTS.size() + " 专家完成）。「" + result.getSummary() + "」");
                }
                updateReviewFull(reviewId, result, combinedJson, expertResults, result.isValid() ? "SUCCESS" : "FAILED");
                Map<String, Object> payload = new HashMap<>();
                if (result.isValid()) {
                    payload.put("scores", JSON.parseObject(result.getScoresJson()));
                    payload.put("overall", result.getOverall());
                    payload.put("risks", JSON.parseArray(result.getRisksJson()));
                    payload.put("summary", result.getSummary());
                    payload.put("checklist", JSON.parseArray(result.getChecklistJson()));
                    payload.put("confidence", result.getConfidence());
                    payload.put("incomplete", incomplete);
                }
                payload.put("experts", expertSummaries(finalResults));
                if (buffer != null) buffer.finish(JSON.toJSONString(payload));
                send(emitter, "done", JSON.toJSONString(payload), null);
                safeComplete(emitter);
            } catch (Exception e) {
                log.error("续跑失败", e);
                sendError(emitter, "续跑失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
            } finally {
                hb.cancel(false);
                ProjectContextHolder.clear();
            }
        });
        return emitter;
    }

    private void ar_aggregating(SseEmitter emitter, com.mokatest.platform.demos.ai.stream.AiStreamBuffer buffer) {
        send(emitter, "aggregating", "{}", buffer);
    }
}
