package com.mokatest.platform.demos.ai.skill;

import com.mokatest.platform.demos.ai.gateway.AiGatewayService;
import com.mokatest.platform.demos.ai.gateway.ChatMessage;
import com.mokatest.platform.demos.ai.gateway.ChatOptions;
import com.mokatest.platform.demos.ai.gateway.ChatResult;
import com.mokatest.platform.demos.ai.service.AiGenerationRecordService;
import com.mokatest.platform.demos.ai.domain.AiGenerationRecord;
import com.mokatest.platform.demos.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Skill 执行支持：编排「上下文组装 → 预算裁剪 → 网关调用 → 解析（失败重试一次）→ 生成记录」
 *
 * 各场景 Controller 只关心：鉴权 → 组装 SkillRequest → 调本组件 → 返回结果。
 * 会话隔离：新会话由本组件创建生成记录；追加会话必须传 recordNo，锚点从记录读取。
 */
@Component
public class SkillExecutionSupport {

    private static final Logger log = LoggerFactory.getLogger(SkillExecutionSupport.class);

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private TokenBudgetTrimmer trimmer;

    @Autowired
    private AiGatewayService gatewayService;

    @Autowired
    private AiGenerationRecordService recordService;

    /**
     * 组装上下文：按 Skill 声明的 ContextSource 列表并行加载（耗时取最慢来源），再做预算裁剪
     *
     * 容错：L1 核心块加载失败向外抛（失去锚点无法生成）；其他层级失败记日志降级跳过。
     */
    public SkillContext buildContext(AiSkill<?> skill, SkillRequest request) {
        SkillContext context = new SkillContext();
        List<ContextSource> sources = new ArrayList<>();
        for (String code : skill.contextSourceCodes()) {
            ContextSource source = skillRegistry.getSource(code);
            if (source == null || !source.supports(skill.scene())) {
                continue;
            }
            if (!request.isContextEnabled(code, source.defaultEnabled())) {
                continue;
            }
            sources.add(source);
        }
        if (sources.size() == 1) {
            ContextBlock block = sources.get(0).load(request);
            if (block != null) {
                context.addBlock(block);
            }
        } else if (!sources.isEmpty()) {
            // 并行加载，按声明顺序 join 保持渲染顺序
            List<CompletableFuture<ContextBlock>> futures = new ArrayList<>();
            for (ContextSource source : sources) {
                futures.add(CompletableFuture.supplyAsync(() -> loadSafely(source, request)));
            }
            for (CompletableFuture<ContextBlock> future : futures) {
                ContextBlock block = future.join();
                if (block != null) {
                    context.addBlock(block);
                }
            }
        }
        trimmer.trim(context);
        return context;
    }

    private ContextBlock loadSafely(ContextSource source, SkillRequest request) {
        try {
            return source.load(request);
        } catch (Exception e) {
            if ("L1".equals(source.level())) {
                throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
            }
            log.warn("上下文来源 {} 加载失败，已降级跳过: {}", source.code(), e.getMessage());
            return null;
        }
    }

    /**
     * 同步执行 Skill（非流式）
     *
     * @param scene   场景
     * @param request 请求（recordNo 为空=新会话，否则为追加）
     * @return 解析后的结构化结果
     */
    public <T> T execute(String scene, SkillRequest request) {
        AiSkill<T> skill = skillRegistry.getSkill(scene);

        // 追加会话：校验锚点，实体归属从记录读取，不信前端传参
        String previousOutput = null;
        AiGenerationRecord record = null;
        if (request.getRecordNo() != null && !request.getRecordNo().isEmpty()) {
            record = recordService.requireValid(request.getRecordNo(), request.getProjectId());
            request.setEntityId(record.getEntityId());
            request.setProjectId(record.getProjectId());
            previousOutput = record.getOutputSnapshot();
        }

        SkillContext context = buildContext(skill, request);
        List<ChatMessage> messages = skill.buildPrompt(request, context, previousOutput);

        ChatOptions options = new ChatOptions();
        options.setScene(scene);
        options.setUserId(request.getUserId());
        options.setTeamId(request.getTeamId());
        options.setProjectId(request.getProjectId());
        options.setPromptSummary(buildSummary(request, context));

        ChatResult result = gatewayService.chat(messages, options);
        try {
            return skill.parse(result.getContent());
        } catch (BusinessException parseError) {
            // 输出非法时重试一次（把错误反馈给模型让其自我修正）
            messages.add(ChatMessage.assistant(result.getContent()));
            messages.add(ChatMessage.user("上一次输出无法解析：" + parseError.getMessage()
                    + "，请严格按要求的 JSON 格式重新输出。"));
            ChatResult retry = gatewayService.chat(messages, options);
            return skill.parse(retry.getContent());
        }
    }

    /**
     * 创建生成记录并返回（首轮生成后由 Controller 调用，返回 recordNo 给前端）
     */
    public AiGenerationRecord createRecord(SkillRequest request, String scene, String outputSnapshot, SkillContext context) {
        AiGenerationRecord record = recordService.create(
                request.getProjectId(), request.getTeamId(), request.getUserId(),
                scene, request.getEntityId(), buildSummary(request, context));
        recordService.appendSnapshot(record.getRecordNo(), request.getProjectId(), outputSnapshot);
        return record;
    }

    private String buildSummary(SkillRequest request, SkillContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("entityId=").append(request.getEntityId());
        if (request.getInstruction() != null && !request.getInstruction().isEmpty()) {
            sb.append("；指令：").append(request.getInstruction().length() > 100
                    ? request.getInstruction().substring(0, 100) : request.getInstruction());
        }
        // 知识库引用审计（检索命中在 buildContext 时已写入 request.citations）
        if (request.getCitations() != null && !request.getCitations().isEmpty()) {
            sb.append("；知识库：");
            request.getCitations().stream()
                    .map(SkillRequest.Citation::getTitle)
                    .distinct()
                    .limit(5)
                    .forEach(t -> sb.append("《").append(t).append("》"));
        }
        if (context != null && !context.getTrimmedNotes().isEmpty()) {
            sb.append("；裁剪：").append(String.join(" | ", context.getTrimmedNotes()));
        }
        return sb.length() > 480 ? sb.substring(0, 480) : sb.toString();
    }
}
