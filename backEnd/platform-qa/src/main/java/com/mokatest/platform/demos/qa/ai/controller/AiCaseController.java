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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    private void runAsync(Runnable task, SseEmitter emitter) {
        Thread thread = new Thread(() -> {
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
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

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
}
