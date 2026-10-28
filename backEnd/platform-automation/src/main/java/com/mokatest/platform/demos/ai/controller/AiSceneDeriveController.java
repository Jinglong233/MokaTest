package com.mokatest.platform.demos.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.ai.service.AiSceneDeriveService;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder;
import com.mokatest.platform.demos.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * AI 派生场景接口（批次二·步骤6）。
 *
 * SSE 事件契约：
 *   delta   生成过程文本增量（前端展示用）
 *   warning 幻觉元素 id 剔除等告警，payload 为 {"message":"..."}
 *   result  最终派生场景草稿 JSON：{name, description, deriveFrom, steps:[{stepType, stepName, stepDetail}]}，
 *           步骤内元素一律以 stepDetail.elementId 引用库元素（已校验 ∈ 勾选范围）
 *   error   失败原因（上下文超限/注入拦截/模型异常/解析失败）
 */
@Slf4j
@RestController
@RequestMapping("/automation/ai/scene")
@RequiredArgsConstructor
public class AiSceneDeriveController {

    private final AiSceneDeriveService aiSceneDeriveService;

    /**
     * 派生场景草稿（SSE）
     * 权限：auto:scene:create
     */
    @SaCheckPermission("auto:scene:create")
    @PostMapping(value = "/derive", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter derive(@RequestBody AiSceneDeriveService.DeriveRequestBody body) {
        SseEmitter emitter = new SseEmitter(180_000L);
        AiSceneDeriveService.DeriveContext context;
        try {
            // 上下文（登录态/项目 ThreadLocal）与 DB 装配必须在请求线程内完成
            context = aiSceneDeriveService.buildContext(body);
        } catch (Exception e) {
            runAsync(() -> { throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e); }, emitter);
            return emitter;
        }
        runAsync(() -> aiSceneDeriveService.deriveStream(context, emitter), emitter);
        return emitter;
    }

    /**
     * 派生确认落库：复用场景新建链路，标记 deriveFrom=源场景id，归入「AI派生场景」目录
     * 权限：auto:scene:create
     */
    @SaCheckPermission("auto:scene:create")
    @PostMapping("/derive/save")
    public SaResult save(@RequestBody AiSceneDeriveService.DeriveSaveBody body) {
        Map<String, Object> result = aiSceneDeriveService.saveDerivedScene(body);
        int saved = (int) result.get("savedSteps");
        int skipped = (int) result.get("skippedSteps");
        String msg = "已保存派生场景（" + saved + " 个步骤）";
        if (skipped > 0) {
            msg += "，" + skipped + " 个步骤因引用无效元素被跳过";
        }
        return SaResult.ok(msg).setData(result);
    }

    private void runAsync(Runnable task, SseEmitter emitter) {
        // 捕获请求线程上下文，传递到异步线程（ThreadLocal 不跨线程，ModelManager 解析项目档案依赖它）
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer teamId = TeamContextHolder.getTeamId();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            ProjectContextHolder.setProjectId(projectId);
            TeamContextHolder.setTeamId(teamId);
            try {
                task.run();
            } catch (Throwable e) {
                log.error("AI 派生场景流异常", e);
                try {
                    String msg = e instanceof BusinessException ? e.getMessage()
                            : "AI 派生失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    emitter.send(SseEmitter.event().name("error").data(msg));
                } catch (Exception ignored) {
                }
                emitter.complete();
            } finally {
                ProjectContextHolder.clear();
                TeamContextHolder.clear();
            }
        });
    }
}
