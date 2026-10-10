package com.mokatest.platform.demos.qa.ai.review;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mokatest.platform.demos.ai.domain.ReviewResult;
import com.mokatest.platform.demos.ai.service.AssistantFactory;
import com.mokatest.platform.demos.ai.review.ReviewAssistant;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.qa.aireview.AiRequirementReview;
import com.mokatest.platform.demos.qa.aireview.AiRequirementReviewMapper;
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
 * 需求评审接口（langchain4j AI Service + 结构化输出 + 输入/输出护栏 + RAG）。
 *
 * 单次调用：前端弹窗填写指令 → 后端拼装需求内容 + 指令 → review() 返回 ReviewResult POJO → 算分 → 落库 → SSE done。
 * 不使用 ChatMemory（无多轮对话）、不使用意图分类（无 REVIEW/CHAT 路由）。
 */
@Slf4j
@RestController
@RequestMapping("/qa/ai/review/agent")
@RequiredArgsConstructor
@SaCheckPermission("qa:ai:use")
public class ReviewAgentController {

    private final AssistantFactory assistantFactory;
    private final RequirementMapper requirementMapper;
    private final AiRequirementReviewMapper reviewMapper;

    /** 评审维度（中文标签 → 英文 key），进度推送和算分共用 */
    private static final String[][] REVIEW_DIMS = {
        {"完整度", "completeness"}, {"清晰度", "clarity"}, {"可测性", "testability"},
        {"一致性", "consistency"}, {"安全性", "security"}, {"边界覆盖", "boundary"}
    };

    /** 进行中的评审上下文：requirementId → ReviewProgressContext（断线恢复用） */
    private static final ConcurrentHashMap<Long, ReviewProgressContext> ACTIVE_REVIEWS = new ConcurrentHashMap<>();

    /** 评审进度上下文：维护维度状态和当前 emitter，支持断线后替换 emitter 恢复推送 */
    private static class ReviewProgressContext {
        final Long requirementId;
        final ConcurrentHashMap<String, String> dimStatus = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, Object> dimScore = new ConcurrentHashMap<>();
        volatile SseEmitter emitter;
        volatile boolean reviewDone = false;
        ReviewProgressContext(Long requirementId, SseEmitter emitter) {
            this.requirementId = requirementId;
            this.emitter = emitter;
            for (String[] dim : REVIEW_DIMS) dimStatus.put(dim[0], "pending");
        }
        String toJsonSnapshot() {
            com.alibaba.fastjson.JSONArray items = new com.alibaba.fastjson.JSONArray();
            for (String[] dim : REVIEW_DIMS) {
                com.alibaba.fastjson.JSONObject item = new com.alibaba.fastjson.JSONObject();
                item.put("dim", dim[0]);
                item.put("status", dimStatus.get(dim[0]));
                Object score = dimScore.get(dim[0]);
                if (score != null) item.put("score", score);
                items.add(item);
            }
            return items.toJSONString();
        }
    }

    @Data
    public static class ChatRequest {
        private Long requirementId;
        private String message;
    }

    /** 发起评审（SSE：status/progress/done/error） */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest body) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(() -> { send(emitter, "error", "评审超时"); safeComplete(emitter); });

        Integer projectId = ProjectContextHolder.getProjectId();
        String userId = StpUtil.getLoginIdAsString();
        Long requirementId = body.getRequirementId();

        if (requirementId == null) {
            runAsync(() -> sendError(emitter, "请指定需求ID"), emitter);
            return emitter;
        }

        Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", requirementId).eq("project_id", projectId)
                .select("id", "title", "description"));
        if (req == null) {
            runAsync(() -> sendError(emitter, "需求不存在"), emitter);
            return emitter;
        }

        String title = req.getTitle() != null ? req.getTitle() : "（无）";
        String cleanDesc = req.getDescription() != null && !req.getDescription().isBlank()
                ? RichTextCleaner.toPlainText(req.getDescription()) : "";
        String requirementText = "标题：" + title;
        if (!cleanDesc.isEmpty()) requirementText += "\n\n描述：\n" + cleanDesc;
        if (requirementText.length() > 8000) {
            requirementText = requirementText.substring(0, 8000) + "\n\n（需求过长，本次仅评审前部）";
        }

        String contentHash = sha1(title + "|" + cleanDesc);
        String instruction = body.getMessage() != null && !body.getMessage().isBlank()
                ? body.getMessage() : "请进行全面评审";
        String userMessage = "需求内容：\n" + requirementText + "\n\n用户指令：" + instruction;

        final String finalRequirementText = requirementText;
        runAsync(() -> doReview(projectId, userId, requirementId, userMessage,
                finalRequirementText, contentHash, instruction, emitter), emitter);
        return emitter;
    }

    /** 清除评审记录（重新评审） */
    @PostMapping("/clear-memory")
    public SaResult clearMemory(@RequestParam Long requirementId) {
        try {
            Integer projectId = ProjectContextHolder.getProjectId();
            reviewMapper.delete(new QueryWrapper<AiRequirementReview>()
                    .eq("requirement_id", requirementId).eq("project_id", projectId));
            requirementMapper.update(null, new UpdateWrapper<Requirement>()
                    .eq("id", requirementId)
                    .set("ai_review_status", null)
                    .set("ai_review_score", null)
                    .set("ai_review_time", null));
            return SaResult.ok("已清除评审记录");
        } catch (Exception e) {
            log.warn("清除评审记录失败: {}", e.getMessage());
            return SaResult.error("清除失败");
        }
    }

    /** 完成评审（锁定当前结果，删除中间记录只保留最终一条） */
    @PostMapping("/finalize")
    public SaResult finalize(@RequestParam Long requirementId) {
        try {
            Integer projectId = ProjectContextHolder.getProjectId();
            AiRequirementReview latest = reviewMapper.selectOne(new QueryWrapper<AiRequirementReview>()
                    .eq("requirement_id", requirementId).eq("project_id", projectId)
                    .orderByDesc("create_time").last("LIMIT 1"));
            if (latest == null) {
                return SaResult.error("暂无评审记录，无法完成评审");
            }
            requirementMapper.update(null, new UpdateWrapper<Requirement>()
                    .eq("id", requirementId)
                    .set("ai_review_status", "FINALIZED"));
            reviewMapper.delete(new QueryWrapper<AiRequirementReview>()
                    .eq("requirement_id", requirementId).eq("project_id", projectId)
                    .ne("id", latest.getId()));
            return SaResult.ok("评审已完成");
        } catch (Exception e) {
            log.warn("锁定评审失败: {}", e.getMessage());
            return SaResult.error("锁定失败");
        }
    }

    /** 查询是否有进行中的评审（断线恢复检测） */
    @GetMapping("/{requirementId}/progress-status")
    public SaResult progressStatus(@PathVariable Long requirementId) {
        ReviewProgressContext ctx = ACTIVE_REVIEWS.get(requirementId);
        return SaResult.ok().setData(java.util.Map.of("active", ctx != null && !ctx.reviewDone));
    }

    /** 恢复评审进度（SSE）：一次性回放全量进度快照（restore），后续 progress/done 正常推送 */
    @PostMapping(value = "/{requirementId}/resume-progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeProgress(@PathVariable Long requirementId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(() -> { safeComplete(emitter); });
        ReviewProgressContext ctx = ACTIVE_REVIEWS.get(requirementId);
        if (ctx == null || ctx.reviewDone) {
            runAsync(() -> { send(emitter, "done", "{}"); safeComplete(emitter); }, emitter);
            return emitter;
        }
        ctx.emitter = emitter;
        runAsync(() -> {
            try { Thread.sleep(100); send(emitter, "restore", ctx.toJsonSnapshot()); }
            catch (Exception e) { log.debug("回放进度异常: {}", e.getMessage()); }
        }, emitter);
        return emitter;
    }

    private void doReview(Integer projectId, String userId, Long requirementId,
                          String userMessage, String requirementText,
                          String contentHash, String instruction, SseEmitter emitter) {
        send(emitter, "status", "正在执行评审…");

        // 创建进度上下文，支持断线恢复
        ReviewProgressContext ctx = new ReviewProgressContext(requirementId, emitter);
        ACTIVE_REVIEWS.put(requirementId, ctx);

        // 进度推送守护线程
        java.util.concurrent.atomic.AtomicBoolean reviewing = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicInteger dimIdx = new java.util.concurrent.atomic.AtomicInteger(0);
        Thread progressThread = new Thread(() -> {
            try {
                while (reviewing.get() && dimIdx.get() < REVIEW_DIMS.length) {
                    Thread.sleep(6000);
                    if (!reviewing.get()) break;
                    int idx = dimIdx.getAndIncrement();
                    if (idx < REVIEW_DIMS.length) {
                        // 之前的 running 维度变为 done（无评分）
                        for (int i = 0; i < idx; i++) {
                            if ("running".equals(ctx.dimStatus.get(REVIEW_DIMS[i][0]))) {
                                ctx.dimStatus.put(REVIEW_DIMS[i][0], "done");
                            }
                        }
                        ctx.dimStatus.put(REVIEW_DIMS[idx][0], "running");
                        com.alibaba.fastjson.JSONObject evt = new com.alibaba.fastjson.JSONObject();
                        evt.put("dim", REVIEW_DIMS[idx][0]);
                        evt.put("status", "running");
                        send(ctx.emitter, "progress", evt.toJSONString());
                    }
                }
            } catch (InterruptedException e) { /* 正常中断 */ }
        });
        progressThread.setDaemon(true);
        progressThread.start();

        try {
            ReviewAssistant assistant = assistantFactory.createReviewAssistant(projectId);
            ReviewResult result = assistant.review(userMessage);

            // 停止进度推送
            reviewing.set(false);
            progressThread.interrupt();

            if (result == null || result.checklist() == null || result.checklist().isEmpty()) {
                ctx.reviewDone = true;
                ACTIVE_REVIEWS.remove(requirementId);
                sendError(ctx.emitter, "AI 未返回有效评审结果");
                return;
            }

            Map<String, Object> payload = scoreAndPersist(projectId, userId, requirementId,
                    result, requirementText, contentHash, instruction);

            // 逐个推送维度完成状态（含评分），更新 context
            @SuppressWarnings("unchecked")
            Map<String, Object> scores = (Map<String, Object>) payload.get("scores");
            for (String[] dim : REVIEW_DIMS) {
                ctx.dimStatus.put(dim[0], "done");
                Object score = scores != null ? scores.get(dim[1]) : null;
                if (score != null) ctx.dimScore.put(dim[0], score);
                com.alibaba.fastjson.JSONObject evt = new com.alibaba.fastjson.JSONObject();
                evt.put("dim", dim[0]);
                evt.put("status", "done");
                if (score != null) evt.put("score", score);
                send(ctx.emitter, "progress", evt.toJSONString());
            }

            ctx.reviewDone = true;
            ACTIVE_REVIEWS.remove(requirementId);
            send(ctx.emitter, "done", JSON.toJSONString(payload));
            safeComplete(ctx.emitter);
        } catch (Exception e) {
            reviewing.set(false);
            progressThread.interrupt();
            ctx.reviewDone = true;
            ACTIVE_REVIEWS.remove(requirementId);
            log.error("评审失败", e);
            sendError(ctx.emitter, "评审失败：" + (e.getMessage() != null ? e.getMessage() : "未知错误"));
        }
    }

    /** 服务端确定性算分 + 落库 */
    private Map<String, Object> scoreAndPersist(Integer projectId, String userId, Long requirementId,
                                                ReviewResult result, String requirementText,
                                                String contentHash, String instruction) {
        String normalizedText = normalize(requirementText);

        // 算分
        Map<String, List<Integer>> dimScores = new LinkedHashMap<>();
        for (String dim : List.of("completeness", "clarity", "testability", "consistency", "security", "boundary")) {
            dimScores.put(dim, new ArrayList<>());
        }
        for (ReviewResult.ChecklistEntry entry : result.checklist()) {
            String dim = dimOfId(entry.id());
            if (dim != null) {
                int score = entry.answer() == ReviewResult.Answer.YES ? 100
                        : entry.answer() == ReviewResult.Answer.PARTIAL ? 50 : 0;
                dimScores.get(dim).add(score);
            }
        }

        Map<String, Object> scores = new LinkedHashMap<>();
        int overallSum = 0;
        int reviewedDimCount = 0;
        for (Map.Entry<String, List<Integer>> e : dimScores.entrySet()) {
            List<Integer> vals = e.getValue();
            if (vals.isEmpty()) continue;
            int avg = (int) vals.stream().mapToInt(Integer::intValue).average().orElse(0);
            scores.put(e.getKey(), avg);
            overallSum += avg;
            reviewedDimCount++;
        }
        int overall = reviewedDimCount > 0 ? overallSum / reviewedDimCount : 0;

        // quote 校验（幻觉拦截）
        int quoteChecked = 0, quoteValid = 0;
        List<Map<String, Object>> checklistJson = new ArrayList<>();
        for (ReviewResult.ChecklistEntry entry : result.checklist()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", entry.id());
            item.put("answer", entry.answer() != null ? entry.answer().name() : "NO");
            item.put("quote", entry.quote() != null ? entry.quote() : "");
            if (entry.quote() != null && !entry.quote().isEmpty()) {
                quoteChecked++;
                if (normalizedText.contains(normalize(entry.quote()))) {
                    quoteValid++;
                } else {
                    item.put("evidenceInvalid", true);
                }
            }
            checklistJson.add(item);
        }
        double passRate = quoteChecked > 0 ? (double) quoteValid / quoteChecked : 1.0;
        String confidence = passRate >= 0.9 ? "HIGH" : passRate >= 0.7 ? "MEDIUM" : "LOW";

        // 风险项
        List<Map<String, Object>> risksJson = new ArrayList<>();
        Set<String> validDims = Set.of("completeness", "clarity", "testability", "consistency", "security", "boundary");
        Set<String> validLevels = Set.of("HIGH", "MEDIUM", "LOW");
        if (result.risks() != null) {
            for (ReviewResult.Risk risk : result.risks()) {
                if (risk.dimension() != null && validDims.contains(risk.dimension())
                        && risk.level() != null && validLevels.contains(risk.level())) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("dimension", risk.dimension());
                    r.put("level", risk.level());
                    r.put("desc", risk.desc());
                    r.put("suggestion", risk.suggestion());
                    r.put("quote", risk.quote());
                    r.put("checklistId", risk.checklistId());
                    risksJson.add(r);
                }
            }
        }

        // 总评
        String summary = result.summary() != null ? result.summary() : buildSummary(scores, overall, checklistJson);

        // 落库
        try {
            AiRequirementReview review = new AiRequirementReview();
            review.setProjectId(projectId);
            review.setRequirementId(requirementId);
            review.setContentHash(contentHash);
            com.alibaba.fastjson.JSONObject rawObj = new com.alibaba.fastjson.JSONObject(true);
            rawObj.put("checklist", checklistJson);
            rawObj.put("risks", risksJson);
            rawObj.put("summary", summary);
            review.setRawText(rawObj.toJSONString());
            review.setInstruction(instruction);
            review.setCreateUserId(userId);
            review.setCreateTime(new Date());
            review.setScoresJson(JSON.toJSONString(scores));
            review.setOverallScore(overall);
            review.setRisksJson(JSON.toJSONString(risksJson));
            review.setChecklistJson(JSON.toJSONString(checklistJson));
            review.setConfidence(confidence);
            review.setSummary(summary);
            review.setStatus("SUCCESS");
            reviewMapper.insert(review);

            Requirement reqUpdate = new Requirement();
            reqUpdate.setId(requirementId.intValue());
            reqUpdate.setAiReviewStatus("REVIEWED");
            reqUpdate.setAiReviewScore(overall);
            reqUpdate.setAiReviewTime(new Date());
            requirementMapper.updateById(reqUpdate);
        } catch (Exception e) {
            log.warn("评审结果落库失败: {}", e.getMessage());
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("scores", scores);
        payload.put("overall", overall);
        payload.put("risks", risksJson);
        payload.put("checklist", checklistJson);
        payload.put("confidence", confidence);
        payload.put("summary", summary);
        return payload;
    }

    private String buildSummary(Map<String, Object> scores, int overall, List<Map<String, Object>> checklist) {
        Map<String, String> dimLabels = new LinkedHashMap<>();
        dimLabels.put("completeness", "完整度"); dimLabels.put("clarity", "清晰度");
        dimLabels.put("testability", "可测性"); dimLabels.put("consistency", "一致性");
        dimLabels.put("security", "安全性"); dimLabels.put("boundary", "边界覆盖");

        StringBuilder sb = new StringBuilder();
        sb.append("本次评审总分 ").append(overall).append("/100。");
        String bestDim = null, worstDim = null;
        int best = -1, worst = 101;
        for (Map.Entry<String, String> e : dimLabels.entrySet()) {
            int s = scores.get(e.getKey()) instanceof Integer ? (Integer) scores.get(e.getKey()) : 0;
            if (s > best) { best = s; bestDim = e.getKey(); }
            if (s < worst) { worst = s; worstDim = e.getKey(); }
        }
        if (bestDim != null && worstDim != null) {
            sb.append(dimLabels.get(worstDim)).append("（").append(worst).append("分）相对薄弱，")
              .append(dimLabels.get(bestDim)).append("（").append(best).append("分）较好。");
        }
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> item : checklist) {
            if ("NO".equals(item.get("answer")) && missing.size() < 5) {
                missing.add(String.valueOf(item.get("id")));
            }
        }
        if (!missing.isEmpty()) {
            sb.append("主要缺失项：").append(String.join("、", missing)).append("。");
        }
        return sb.toString();
    }

    private static String dimOfId(String id) {
        if (id == null) return null;
        if (id.startsWith("CMP")) return "completeness";
        if (id.startsWith("CLR")) return "clarity";
        if (id.startsWith("TST")) return "testability";
        if (id.startsWith("CON")) return "consistency";
        if (id.startsWith("SEC")) return "security";
        if (id.startsWith("BND")) return "boundary";
        return null;
    }

    private static String normalize(String s) { return s == null ? "" : s.replaceAll("\\s+", ""); }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private void send(SseEmitter emitter, String event, String data) {
        try { emitter.send(SseEmitter.event().name(event).data(data)); }
        catch (IOException | IllegalStateException e) { log.debug("SSE 推送失败: {}", e.getMessage()); }
    }
    private void safeComplete(SseEmitter emitter) { try { emitter.complete(); } catch (Exception ignored) {} }
    private void sendError(SseEmitter emitter, String msg) { send(emitter, "error", msg); safeComplete(emitter); }
    private void runAsync(Runnable task, SseEmitter emitter) {
        // 捕获请求线程上下文，传递到异步线程（ThreadLocal 不跨线程，ModelManager 解析项目档案依赖它）
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer teamId = com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.getTeamId();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            ProjectContextHolder.setProjectId(projectId);
            com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.setTeamId(teamId);
            try { task.run(); } catch (Exception e) { sendError(emitter, e.getMessage()); }
            finally {
                ProjectContextHolder.clear();
                com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.clear();
            }
        });
    }
}
