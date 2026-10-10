package com.mokatest.platform.demos.qa.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.ai.skill.SkillRequest;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.qa.ai.casegen.CaseDraftDTO;
import com.mokatest.platform.demos.qa.ai.service.AiCaseService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.qa.aireview.AiRequirementReview;
import com.mokatest.platform.demos.qa.aireview.AiRequirementReviewMapper;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import com.mokatest.platform.demos.qa.mapper.TestCaseMapper;

import java.util.List;
import java.util.Map;

/**
 * AI 生成测试用例接口（场景一）
 *
 * 会话隔离规则：append/adopt 只传 recordNo，锚定需求与项目归属后端从记录读取。
 * projectId/teamId 一律来自请求头上下文（X-Project-Id / X-Team-Id），不信 body 传参。
 *
 * 权限：qa:testcase:create（生成/追加/入库）、qa:testcase:view（记录回溯）
 */
@Slf4j
@RestController
@RequestMapping("/qa/ai/case")
@RequiredArgsConstructor
public class AiCaseController {

    private final AiCaseService aiCaseService;
    private final com.mokatest.platform.demos.ai.stream.AiStreamReattachSupport reattachSupport;
    private final RequirementMapper requirementMapper;
    private final TestCaseMapper testCaseMapper;
    private final AiRequirementReviewMapper reviewMapper;

    /**
     * 流式生成测试用例（新会话，SSE）
     *
     * 注意：SSE 端点禁止在请求线程抛异常（会走 JSON 异常处理、截断流），
     * 所有校验与业务调用均在异步线程内执行，失败通过 error 事件推送。
     */
    @SaCheckPermission("qa:testcase:create")
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@RequestBody GenerateRequest body) {
        // 上下文（登录态/团队/项目 ThreadLocal）必须在请求线程内解析；
        // 但 SSE 端点不能向请求线程抛异常（异常处理器返回 JSON 与 text/event-stream 冲突会截断流），
        // 因此解析失败也转为 SSE error 事件
        SseEmitter emitter = new SseEmitter(180_000L);
        SkillRequest request;
        try {
            request = aiCaseService.buildRequest(
                    body.getRequirementId(), null, body.getInstruction());
        } catch (Exception e) {
            runAsync(() -> { throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e); }, emitter);
            return emitter;
        }
        runAsync(() -> aiCaseService.generateStream(request, false, emitter), emitter);
        return emitter;
    }

    /**
     * 追加生成（同会话继续，SSE）
     */
    @SaCheckPermission("qa:testcase:create")
    @PostMapping(value = "/append", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter append(@RequestBody AppendRequest body) {
        SseEmitter emitter = new SseEmitter(180_000L);
        SkillRequest request;
        try {
            request = aiCaseService.buildRequest(
                    null, body.getRecordNo(), body.getInstruction());
        } catch (Exception e) {
            runAsync(() -> { throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e); }, emitter);
            return emitter;
        }
        runAsync(() -> aiCaseService.generateStream(request, true, emitter), emitter);
        return emitter;
    }

    /**
     * 采纳入库：勾选草稿批量保存为正式用例
     */
    @SaCheckPermission("qa:testcase:create")
    @PostMapping("/adopt")
    public SaResult adopt(@RequestBody AdoptRequest body) {
        Map<String, Object> result = aiCaseService.adopt(body.getRecordNo(), body.getItems());
        int saved = ((List<?>) result.get("savedIds")).size();
        int skipped = (int) result.get("skippedCount");
        String msg = "已入库 " + saved + " 条用例";
        if (skipped > 0) {
            msg += "，" + skipped + " 条已入库过自动跳过";
        }
        return SaResult.ok(msg).setData(result);
    }

    /**
     * 生成记录回溯列表
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/records")
    public SaResult records(@RequestParam Long requirementId) {
        return SaResult.ok().setData(aiCaseService.listRecords(requirementId));
    }

    /**
     * 重连生成流：关弹窗后重新打开时回放并接续实时流（SSE）
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String recordNo) {
        SseEmitter emitter = new SseEmitter(180_000L);
        reattachSupport.reattach(recordNo,
                com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder.getProjectId(), emitter);
        return emitter;
    }

    /**
     * 停止生成：取消底层模型调用，保留已生成部分内容
     */
    @SaCheckPermission("qa:testcase:create")
    @PostMapping("/stop")
    public SaResult stop(@RequestParam String recordNo) {
        aiCaseService.stop(recordNo);
        return SaResult.ok("已停止");
    }

    /**
     * 重新生成：删除最后一轮，按同一指令重跑（SSE）
     */
    @SaCheckPermission("qa:testcase:create")
    @PostMapping(value = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@RequestParam String recordNo) {
        SseEmitter emitter = new SseEmitter(180_000L);
        SkillRequest baseRequest;
        try {
            // 请求线程内解析上下文（StpUtil/ProjectContextHolder 仅在此可用）
            baseRequest = aiCaseService.buildRequest(null, recordNo, null);
        } catch (Exception e) {
            runAsync(() -> { throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e); }, emitter);
            return emitter;
        }
        runAsync(() -> aiCaseService.regenerateStream(baseRequest, emitter), emitter);
        return emitter;
    }

    /**
     * 删除会话记录（不影响已入库的用例）
     */
    @SaCheckPermission("qa:testcase:create")
    @PostMapping("/deleteRecord")
    public SaResult deleteRecord(@RequestParam String recordNo) {
        aiCaseService.deleteRecord(recordNo);
        return SaResult.ok("已删除");
    }

    /**
     * 归档会话记录（标记为 ARCHIVED，不影响已入库用例，列表排除已归档）
     */
    @SaCheckPermission("qa:testcase:create")
    @PostMapping("/archive")
    public SaResult archiveRecord(@RequestParam String recordNo) {
        aiCaseService.archiveRecord(recordNo);
        return SaResult.ok("会话已归档");
    }

    private void runAsync(Runnable task, SseEmitter emitter) {
        // 捕获请求线程上下文，传递到异步线程（ThreadLocal 不跨线程，ModelManager 解析项目档案依赖它）
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer teamId = com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.getTeamId();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            ProjectContextHolder.setProjectId(projectId);
            com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.setTeamId(teamId);
            try {
                task.run();
            } catch (Throwable e) {
                log.error("AI 生成流异常", e);
                try {
                    String msg = e instanceof BusinessException ? e.getMessage()
                            : "AI 生成失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    emitter.send(SseEmitter.event().name("error").data(msg));
                } catch (Exception ignored) {
                }
                emitter.complete();
            } finally {
                ProjectContextHolder.clear();
                com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.clear();
            }
        });
    }

    // ==================== Phase 4: 四阶段接口 ====================

    /**
     * 覆盖度审查（用户触发，同步返回审查结论）
     */
    @SaCheckPermission("qa:ai:use")
    @PostMapping("/auditCoverage")
    public SaResult auditCoverage(@RequestBody java.util.Map<String, String> body) {
        String recordNo = body.get("recordNo");
        Integer projectId = ProjectContextHolder.getProjectId();
        return SaResult.ok().setData(aiCaseService.auditCoverage(recordNo, projectId));
    }

    /**
     * 阶段1：生成设计维度点（SSE 流式）
     */
    @SaCheckPermission("qa:ai:use")
    @PostMapping(value = "/points/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter pointsStream(@RequestBody PointsRequest body) {
        SseEmitter emitter = new SseEmitter(180_000L);
        SkillRequest request;
        try {
            request = aiCaseService.buildRequest(body.getRequirementId(), null, body.getInstruction());
            request.setCount(body.getCount());
        } catch (Exception e) {
            runAsync(() -> { throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e); }, emitter);
            return emitter;
        }
        runAsync(() -> aiCaseService.pointsStream(request, emitter), emitter);
        return emitter;
    }

    /**
     * 保存用户确认/编辑后的设计维度点
     */
    @SaCheckPermission("qa:ai:use")
    @PutMapping("/record/{recordNo}/testPoints")
    public SaResult saveTestPoints(@PathVariable String recordNo, @RequestBody String testPointsJson) {
        aiCaseService.saveTestPoints(recordNo,
                com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder.getProjectId(),
                testPointsJson);
        return SaResult.ok("设计维度点已保存");
    }

    /**
     * 阶段2：用例生成（SSE 流式）
     */
    @SaCheckPermission("qa:ai:use")
    @PostMapping(value = "/cases/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter casesStream(@RequestBody CasesRequest body) {
        // 15 分钟：自动补全循环最多 3 轮生成 + 1 次审查，5 分钟墙钟不够
        SseEmitter emitter = new SseEmitter(900_000L);
        emitter.onTimeout(() -> {
            try {
                emitter.send(SseEmitter.event().name("error").data("生成超时，请重试"));
                emitter.complete();
            } catch (Exception ignored) {}
        });
        SkillRequest request;
        try {
            request = aiCaseService.buildRequest(body.getRequirementId(), body.getRecordNo(), body.getInstruction());
            request.setCount(body.getCount());
            request.setTargetTpId(body.getTargetTpId());
        } catch (Exception e) {
            runAsync(() -> { throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e); }, emitter);
            return emitter;
        }
        runAsync(() -> aiCaseService.casesStream(request, emitter), emitter);
        return emitter;
    }

    /**
     * 全量状态恢复（断线重连用）
     */
    @SaCheckPermission("qa:ai:use")
    @GetMapping("/record/{recordNo}")
    public SaResult getRecord(@PathVariable String recordNo) {
        return SaResult.ok().setData(aiCaseService.getRecordState(recordNo,
                com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder.getProjectId()));
    }

    /**
     * 增量保存草稿（前端就地编辑后调用）
     */
    @SaCheckPermission("qa:ai:use")
    @PutMapping("/record/{recordNo}/drafts")
    public SaResult saveDrafts(@PathVariable String recordNo, @RequestBody String draftsJson) {
        aiCaseService.saveDrafts(recordNo,
                com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder.getProjectId(),
                draftsJson);
        return SaResult.ok("草稿已保存");
    }

    @Data
    public static class PointsRequest {
        private Long requirementId;
        private String instruction;
        private Integer count;
    }

    @Data
    public static class CasesRequest {
        private String recordNo;
        private String instruction;
        private Integer count;
        /** 新流程（无设计点环节）：无 recordNo 时按需求直接发起生成 */
        private Long requirementId;
        /** 单维度重生：只为该维度点生成用例（指令由后端拼装） */
        private String targetTpId;
    }

    // ==================== 旧版接口（兼容） ====================

    @Data
    public static class GenerateRequest {
        private Long requirementId;
        private String instruction;
    }

    @Data
    public static class AppendRequest {
        private String recordNo;
        private String instruction;
    }

    @Data
    public static class AdoptRequest {
        private String recordNo;
        private List<CaseDraftDTO> items;
    }

    // ==================== 需求摘要（Card 1 用） ====================

    @SaCheckPermission("qa:ai:use")
    @GetMapping("/req-summary/{reqId}")
    public SaResult getReqSummary(@PathVariable Long reqId) {
        Integer projectId = ProjectContextHolder.getProjectId();
        Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                .eq("id", reqId).eq("project_id", projectId)
                .select("id", "req_code", "title", "status", "priority"));
        if (req == null) return SaResult.error("需求不存在");
        Long caseCount = testCaseMapper.selectCount(new QueryWrapper<TestCase>()
                .eq("project_id", projectId).eq("requirement_id", reqId));
        AiRequirementReview latestReview = reviewMapper.selectOne(new QueryWrapper<AiRequirementReview>()
                .eq("requirement_id", reqId)
                .eq("project_id", projectId)
                .orderByDesc("create_time").last("limit 1"));
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", req.getId());
        result.put("reqCode", req.getReqCode());
        result.put("title", req.getTitle());
        result.put("status", req.getStatus());
        result.put("priority", req.getPriority());
        result.put("caseCount", caseCount);
        result.put("aiReviewScore", latestReview != null ? latestReview.getOverallScore() : null);
        // §10.2-1：检查是否存在已归档且审查通过的会话
        result.putAll(aiCaseService.getArchivedAuditSummary(reqId, projectId));
        return SaResult.ok().setData(result);
    }
}
