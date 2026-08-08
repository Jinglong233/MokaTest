package com.mokatest.platform.demos.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.ai.service.AiApiCaseService;
import com.mokatest.platform.demos.ai.skill.SkillRequest;
import com.mokatest.platform.demos.ai.skill.apicase.ApiCaseDraftDTO;
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
 * AI 生成 API 接口用例接口（场景四）
 *
 * 权限：auto:api:create（生成/追加/入库）、auto:api:view（记录回溯）
 */
@Slf4j
@RestController
@RequestMapping("/ai/apiCase")
@RequiredArgsConstructor
public class AiApiCaseController {

    private final AiApiCaseService aiApiCaseService;
    private final com.mokatest.platform.demos.ai.stream.AiStreamReattachSupport reattachSupport;

    @SaCheckPermission("auto:api:create")
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(@RequestBody GenerateRequest body) {
        SseEmitter emitter = new SseEmitter(180_000L);
        SkillRequest request;
        try {
            // 上下文（登录态/团队/项目 ThreadLocal）必须在请求线程内解析
            request = aiApiCaseService.buildRequest(
                    body.getApiId(), null, body.getInstruction());
        } catch (Exception e) {
            // SSE 端点不能向请求线程抛异常，转为 error 事件
            runAsync(() -> { throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e); }, emitter);
            return emitter;
        }
        runAsync(() -> aiApiCaseService.generateStream(request, false, emitter), emitter);
        return emitter;
    }

    @SaCheckPermission("auto:api:create")
    @PostMapping(value = "/append", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter append(@RequestBody AppendRequest body) {
        SseEmitter emitter = new SseEmitter(180_000L);
        SkillRequest request;
        try {
            request = aiApiCaseService.buildRequest(
                    null, body.getRecordNo(), body.getInstruction());
        } catch (Exception e) {
            runAsync(() -> { throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e); }, emitter);
            return emitter;
        }
        runAsync(() -> aiApiCaseService.generateStream(request, true, emitter), emitter);
        return emitter;
    }

    @SaCheckPermission("auto:api:create")
    @PostMapping("/adopt")
    public SaResult adopt(@RequestBody AdoptRequest body) {
        Map<String, Object> result = aiApiCaseService.adopt(body.getRecordNo(), body.getItems());
        int saved = ((List<?>) result.get("savedIds")).size();
        int skipped = (int) result.get("skippedCount");
        String msg = "已入库 " + saved + " 条用例";
        if (skipped > 0) {
            msg += "，" + skipped + " 条已入库过自动跳过";
        }
        return SaResult.ok(msg).setData(result);
    }

    @SaCheckPermission("auto:api:view")
    @GetMapping("/records")
    public SaResult records(@RequestParam Long apiId) {
        return SaResult.ok().setData(aiApiCaseService.listRecords(apiId));
    }

    /**
     * 重连生成流：关弹窗后重新打开时回放并接续实时流（SSE）
     */
    @SaCheckPermission("auto:api:view")
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
    @SaCheckPermission("auto:api:create")
    @PostMapping("/stop")
    public SaResult stop(@RequestParam String recordNo) {
        aiApiCaseService.stop(recordNo);
        return SaResult.ok("已停止");
    }

    /**
     * 重新生成：删除最后一轮，按同一指令重跑（SSE）
     */
    @SaCheckPermission("auto:api:create")
    @PostMapping(value = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@RequestParam String recordNo) {
        SseEmitter emitter = new SseEmitter(180_000L);
        SkillRequest baseRequest;
        try {
            baseRequest = aiApiCaseService.buildRequest(null, recordNo, null);
        } catch (Exception e) {
            runAsync(() -> { throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e); }, emitter);
            return emitter;
        }
        runAsync(() -> aiApiCaseService.regenerateStream(baseRequest, emitter), emitter);
        return emitter;
    }

    /**
     * 删除会话记录（不影响已入库的用例）
     */
    @SaCheckPermission("auto:api:create")
    @PostMapping("/deleteRecord")
    public SaResult deleteRecord(@RequestParam String recordNo) {
        aiApiCaseService.deleteRecord(recordNo);
        return SaResult.ok("已删除");
    }

    private void runAsync(Runnable task, SseEmitter emitter) {
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } catch (Throwable e) {
                log.error("AI 生成流异常", e);
                try {
                    String msg = e instanceof com.mokatest.platform.demos.exception.BusinessException ? e.getMessage()
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
        private Long apiId;
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
        private List<ApiCaseDraftDTO> items;
    }
}
