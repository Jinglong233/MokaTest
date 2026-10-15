package com.mokatest.platform.demos.qa.ai.agent;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.domain.AiSkillDefinition;
import com.mokatest.platform.demos.ai.mapper.AiSkillDefinitionMapper;
import com.mokatest.platform.demos.ai.service.AgentAssistant;
import com.mokatest.platform.demos.ai.service.AssistantFactory;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.qa.ai.agent.mapper.AgentMessageMapper;
import com.mokatest.platform.demos.qa.ai.agent.mapper.AgentSessionMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

/**
 * AI Agent 接口（Phase 5）
 *
 * 重写后：用 LangChain4j AgentAssistant（AI Service + function calling + ChatMemory），
 * 替代旧手写工具循环 + JSON tool_calls 协议 + CountDownLatch。
 * 工具通过 ToolSpecification/ToolExecutor 闭包适配（projectId 在闭包内捕获，不经模型）。
 */
@Slf4j
@RestController
@RequestMapping("/qa/ai/agent")
@RequiredArgsConstructor
@SaCheckPermission("qa:ai:use")
public class AgentController {

    /** SSE 心跳（长工具执行期间保持连接，防中间代理空闲掐断） */
    private static final java.util.concurrent.ScheduledExecutorService HEARTBEAT =
            java.util.concurrent.Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "agent-sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    /** 服务端停止标志：sessionId → 请求取消（前端 abort 只断连接，LLM 仍在跑，需显式通知） */
    private static final Map<Long, Boolean> CANCEL_FLAGS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 工具执行开始时间（requestId → [sessionId, epochMs]），用于计算 duration 展示 + 按 sessionId 清理 */
    private static final Map<String, long[]> TOOL_START_TIMES = new java.util.concurrent.ConcurrentHashMap<>();

    /** 进行中的流式会话上下文：sessionId → StreamContext（断线恢复用） */
    private static final Map<Long, StreamContext> ACTIVE_STREAMS = new java.util.concurrent.ConcurrentHashMap<>();

    /** 流式会话上下文：维护 fullText 和当前 emitter，支持断线后替换 emitter 恢复推送 */
    private static class StreamContext {
        final Long sessionId;
        final StringBuffer fullText = new StringBuffer();
        volatile SseEmitter emitter;
        volatile boolean done = false;
        StreamContext(Long sessionId, SseEmitter emitter) { this.sessionId = sessionId; this.emitter = emitter; }
    }

    private final AgentToolRegistry toolRegistry;
    private final AgentSessionMapper sessionMapper;
    private final AgentMessageMapper messageMapper;
    private final AssistantFactory assistantFactory;
    private final AiSkillDefinitionMapper skillDefMapper;

    // ==================== 会话管理 ====================

    @PostMapping("/session")
    public SaResult createSession(@RequestBody(required = false) Map<String, Object> body) {
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer userId = StpUtil.getLoginIdAsInt();
        AgentSession session = new AgentSession();
        session.setProjectId(projectId);
        session.setUserId(userId);
        session.setTitle(body != null && body.get("title") != null ? body.get("title").toString() : "新会话");
        session.setCreateTime(new Date());
        sessionMapper.insert(session);
        return SaResult.ok().setData(session);
    }

    @GetMapping("/sessions")
    public SaResult listSessions() {
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer userId = StpUtil.getLoginIdAsInt();
        List<AgentSession> list = sessionMapper.selectList(new QueryWrapper<AgentSession>()
                .eq("project_id", projectId).eq("user_id", userId).orderByDesc("update_time"));
        return SaResult.ok().setData(list);
    }

    @DeleteMapping("/session/{id}")
    public SaResult deleteSession(@PathVariable Long id) {
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer userId = StpUtil.getLoginIdAsInt();
        AgentSession session = sessionMapper.selectById(id);
        if (session == null || !session.getProjectId().equals(projectId) || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在");
        }
        sessionMapper.deleteById(id);
        // 同步驱逐 LangChain4j 聊天记忆（ChatMemoryAccess 标准用法）
        try {
            assistantFactory.createAgent(projectId).evictChatMemory("agent_" + id);
        } catch (Exception e) {
            // 记忆驱逐失败不影响删除主流程
        }
        return SaResult.ok("已删除");
    }

    @GetMapping("/session/{id}/messages")
    public SaResult listMessages(@PathVariable Long id) {
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer userId = StpUtil.getLoginIdAsInt();
        AgentSession session = sessionMapper.selectById(id);
        if (session == null || !session.getProjectId().equals(projectId) || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在");
        }
        List<AgentMessage> list = messageMapper.selectList(new QueryWrapper<AgentMessage>()
                .eq("session_id", id).orderByAsc("create_time"));
        return SaResult.ok().setData(list);
    }

    @PostMapping("/session/{id}/card")
    public SaResult saveCard(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer userId = StpUtil.getLoginIdAsInt();
        AgentSession session = sessionMapper.selectById(id);
        if (session == null || !session.getProjectId().equals(projectId) || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在");
        }
        String cardType = body != null ? String.valueOf(body.get("cardType")) : "";
        Object data = body != null && body.get("data") != null ? JSON.toJSON(body.get("data")) : new JSONObject();
        JSONObject cardContent = new JSONObject(true);
        cardContent.put("cardType", cardType);
        cardContent.put("data", data);
        // 卡片状态回写（如草稿卡入库后置为已保存）：带 messageId 且属于本会话的卡片消息原地更新，否则新插
        Long messageId = body != null && body.get("messageId") != null
                ? Long.valueOf(body.get("messageId").toString()) : null;
        if (messageId != null) {
            AgentMessage exist = messageMapper.selectById(messageId);
            if (exist != null && exist.getSessionId().equals(id) && "card".equals(exist.getRole())) {
                AgentMessage upd = new AgentMessage();
                upd.setId(messageId);
                upd.setContent(cardContent.toJSONString());
                messageMapper.updateById(upd);
                return SaResult.ok("已保存").setData(messageId);
            }
        }
        Long msgId = saveMessage(id, "card", cardContent.toJSONString(), null, null);
        return SaResult.ok("已保存").setData(msgId);
    }

    // ==================== 对话（SSE） ====================

    /** 停止生成：服务端协作式取消（停止推送 + 落库半截内容，LLM 后续输出被丢弃） */
    @PostMapping("/chat/stop")
    public SaResult stopChat(@RequestBody Map<String, Object> body) {
        Long sessionId = body != null && body.get("sessionId") != null
                ? Long.valueOf(body.get("sessionId").toString()) : null;
        if (sessionId == null) {
            throw new BusinessException("请指定会话ID");
        }
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer userId = StpUtil.getLoginIdAsInt();
        AgentSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getProjectId().equals(projectId) || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在或无权访问");
        }
        CANCEL_FLAGS.put(sessionId, true);
        return SaResult.ok("已请求停止");
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest body) {
        SseEmitter emitter = new SseEmitter(300_000L);
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer userId = StpUtil.getLoginIdAsInt();

        AgentSession session = sessionMapper.selectById(body.getSessionId());
        if (session == null || !session.getProjectId().equals(projectId) || !session.getUserId().equals(userId)) {
            runAsync(() -> sendError(emitter, "会话不存在或无权访问"), emitter);
            return emitter;
        }

        AgentContext ctx = new AgentContext();
        ctx.setProjectId(projectId);
        ctx.setTeamId(com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.getTeamId());
        ctx.setUserId(StpUtil.getLoginIdAsString());
        ctx.setPermissions(StpUtil.getPermissionList());

        runAsync(() -> doAgentChat(session, ctx, body.getMessage(), body.getPartialContent(), body.getSkillScene(), Boolean.TRUE.equals(body.getRetry()), emitter), emitter);
        return emitter;
    }

    /** 查询会话是否有进行中的流式回复（断线恢复检测） */
    @GetMapping("/session/{id}/stream-status")
    public SaResult streamStatus(@PathVariable Long id) {
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer userId = StpUtil.getLoginIdAsInt();
        AgentSession session = sessionMapper.selectById(id);
        if (session == null || !session.getProjectId().equals(projectId) || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在");
        }
        StreamContext ctx = ACTIVE_STREAMS.get(id);
        if (ctx == null || ctx.done) {
            return SaResult.ok().setData(java.util.Map.of("active", false));
        }
        return SaResult.ok().setData(java.util.Map.of("active", true, "done", false));
    }

    /** 恢复流式对话：一次性回放全量 fullText（restore 事件），后续 delta 正常推送 */
    @PostMapping(value = "/chat/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter resumeChat(@RequestBody java.util.Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(300_000L);
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer userId = StpUtil.getLoginIdAsInt();

        Long sessionId = body.get("sessionId") != null ? Long.valueOf(body.get("sessionId").toString()) : null;
        if (sessionId == null) {
            runAsync(() -> sendError(emitter, "请指定会话ID"), emitter);
            return emitter;
        }
        AgentSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getProjectId().equals(projectId) || !session.getUserId().equals(userId)) {
            runAsync(() -> sendError(emitter, "会话不存在或无权访问"), emitter);
            return emitter;
        }

        StreamContext ctx = ACTIVE_STREAMS.get(sessionId);
        if (ctx == null || ctx.done) {
            // 流已结束，前端从数据库加载最终结果即可
            runAsync(() -> {
                send(emitter, "done", "{\"sessionId\":\"" + sessionId + "\"}");
                safeComplete(emitter);
            }, emitter);
            return emitter;
        }

        // 替换 emitter：后续 onPartialResponse 回调会推送到新 emitter
        ctx.emitter = emitter;

        // 心跳
        java.util.concurrent.ScheduledFuture<?> hb = HEARTBEAT.scheduleAtFixedRate(
                () -> send(ctx.emitter, "ping", "{}"), 15, 15, java.util.concurrent.TimeUnit.SECONDS);

        // 一次性回放全量已累积文本（restore 事件），前端直接替换 content
        runAsync(() -> {
            try {
                Thread.sleep(100); // 确保 emitter 已注册到 HTTP 容器
                String snapshot = ctx.fullText.toString(); // StringBuffer 线程安全读取
                if (!snapshot.isEmpty()) {
                    send(emitter, "restore", JSON.toJSONString(snapshot));
                }
                // 后续 delta 由 onPartialResponse 回调推送到 ctx.emitter（新 emitter）
            } catch (Exception e) {
                log.debug("回放恢复异常: {}", e.getMessage());
            }
        }, emitter);

        return emitter;
    }

    /** Agent 对话：AgentAssistant.chatStream（原生 function calling + ChatMemory 多轮记忆） */
    private void doAgentChat(AgentSession session, AgentContext agentCtx, String userMessage,
                             String partialContent, String skillSceneParam, boolean retry, SseEmitter emitter) {
        String skillScene = skillSceneParam; // 可能在方法内被重新赋值（会话绑定逻辑）
        Long sessionId = session.getId();
        CANCEL_FLAGS.remove(sessionId);
        // 创建流式上下文，支持断线恢复：emitter 可被 resumeChat 替换
        StreamContext ctx = new StreamContext(sessionId, emitter);
        ACTIVE_STREAMS.put(sessionId, ctx);
        java.util.concurrent.ScheduledFuture<?> hb = HEARTBEAT.scheduleAtFixedRate(
                () -> send(ctx.emitter, "ping", "{}"), 15, 15, java.util.concurrent.TimeUnit.SECONDS);
        // 继续生成场景的占位消息ID（声明在 try 外，catch 里做失败落库要用）
        final Long[] partialMsgId = {null};
        try {
            // §7.2 会话绑定：选定即固定，不可换
            String boundScene = session.getSkillScene();
            if (boundScene != null && skillScene != null && !boundScene.equals(skillScene)) {
                sendError(emitter, "本会话已绑定其他快捷指令（" + boundScene + "），请新开会话后再使用该指令");
                return;
            }
            if (boundScene != null && (skillScene == null || skillScene.isBlank())) {
                skillScene = boundScene;
            }

            // 0. 消息落库分流：retry 重跑（取库里最后一条 user，清失败记录）/ 继续生成 / 首次发送
            if (retry) {
                AgentMessage lastUser = messageMapper.selectOne(new QueryWrapper<AgentMessage>()
                        .eq("session_id", sessionId).eq("role", "user").orderByDesc("id").last("LIMIT 1"));
                if (lastUser == null) {
                    sendError(emitter, "没有可重试的消息");
                    return;
                }
                userMessage = lastUser.getContent();
                if (lastUser.getSkillScene() != null) skillScene = lastUser.getSkillScene();
                // 清掉失败 assistant + tool 记录（id > lastUser.id，同 session_id 范围内）
                messageMapper.delete(new QueryWrapper<AgentMessage>()
                        .eq("session_id", sessionId).gt("id", lastUser.getId()));
            } else if (partialContent != null && !partialContent.isBlank()) {
                partialMsgId[0] = saveMessage(sessionId, "assistant", partialContent, null, null);
            } else {
                // §7.2 用户消息落库带 skill_scene
                saveMessage(sessionId, "user", userMessage, null, null, skillScene);
                if ("新会话".equals(session.getTitle()) && userMessage != null) {
                    session.setTitle(userMessage.length() > 30 ? userMessage.substring(0, 30) + "..." : userMessage);
                    session.setUpdateTime(new Date());
                    sessionMapper.updateById(session);
                }
            }

            // 1. system prompt：快捷指令注入（skillScene 有值时查模板拼入）
            String memoryId = "agent_" + sessionId;

            // 2. 适配工具：AgentTool → ToolSpecification + ToolExecutor（闭包捕获 ctx）
            // §7.2 工具恒带（不再按 tools_enabled 摘除）
            Map<ToolSpecification, ToolExecutor> tools = adaptTools(agentCtx);

            // 查快捷指令模板（校验归属项目 + enabled）
            String skillPrompt = null;
            if (skillScene != null && !skillScene.isBlank()) {
                AiSkillDefinition skillDef = skillDefMapper.selectOne(new QueryWrapper<AiSkillDefinition>()
                        .eq("project_id", agentCtx.getProjectId())
                        .eq("scene", skillScene)
                        .eq("enabled", 1));
                if (skillDef != null && skillDef.getSystemPrompt() != null && !skillDef.getSystemPrompt().isBlank()) {
                    skillPrompt = skillDef.getSystemPrompt();
                    // 首次绑定 → 写 session.skillScene（绑定标记，非能力快照）
                    if (boundScene == null) {
                        session.setSkillScene(skillScene);
                        sessionMapper.updateById(session);
                    }
                } else {
                    // P2-1 skill 禁用/删除 → 推 skill_invalid 事件，前端移除绑定标识
                    log.warn("快捷指令不存在或未启用: scene={}, projectId={}", skillScene, agentCtx.getProjectId());
                    send(emitter, "skill_invalid", JSON.toJSONString(
                            java.util.Map.of("skill", skillScene, "msg", "指令不存在或已被禁用")));
                }
            }

            // §7.2 护栏段单版本 + skill 模板组合；无 skill 时走默认 system prompt
            // 固化当前生效 scene 供流式回调使用（归因落库；lambda 需要 effectively-final）
            final String msgSkillScene = skillScene;
            AgentAssistant assistant;
            if (skillPrompt != null) {
                String combinedPrompt = com.mokatest.platform.demos.ai.service.AgentGuardrail.compose(skillPrompt);
                log.info("快捷指令注入: session={}, scene={}, prompt长度={}", sessionId, skillScene, combinedPrompt.length());
                log.debug("快捷指令 system prompt: {}", combinedPrompt);
                assistant = assistantFactory.createAgentWithSystemPrompt(agentCtx.getProjectId(), combinedPrompt, tools);
            } else {
                assistant = assistantFactory.createAgent(agentCtx.getProjectId(), tools);
            }

            // 3. 流式对话
            String instruction = (partialContent != null && !partialContent.isBlank())
                    ? "请继续输出刚才未完成的内容，不要重复已有内容。"
                    : userMessage;

            final StringBuffer fullText = ctx.fullText;
            TokenStream ts = assistant.chatStream(memoryId, instruction);
            ts.onPartialResponse(token -> {
                        if (CANCEL_FLAGS.containsKey(sessionId)) return;
                        fullText.append(token);
                        send(ctx.emitter, "delta", token);
                    })
                    // 工具开始执行即推卡片（主流交互：「正在调用 xx」，不等执行完）
                    .beforeToolExecution(bte -> {
                        TOOL_START_TIMES.put(bte.request().id(), new long[]{sessionId, System.currentTimeMillis()});
                        JSONObject evt = new JSONObject();
                        evt.put("tool", bte.request().name());
                        try {
                            evt.put("args", JSON.parse(bte.request().arguments()));
                        } catch (Exception e) {
                            evt.put("args", bte.request().arguments());
                        }
                        send(ctx.emitter, "tool", evt.toJSONString());
                    })
                    // RAG 引用溯源：检索命中时推给前端「依据《XX 文档》」展示
                    .onRetrieved(contents -> {
                        List<String> titles = contents.stream()
                                .map(c -> c.textSegment().metadata().getString("docTitle"))
                                .filter(java.util.Objects::nonNull)
                                .distinct().limit(5).toList();
                        if (!titles.isEmpty()) {
                            try {
                                send(ctx.emitter, "citations",
                                        com.alibaba.fastjson.JSON.toJSONString(titles));
                            } catch (Exception ignored) {
                            }
                        }
                    })
                    .onToolExecuted(exec -> onToolExecuted(ctx.emitter, sessionId, exec))
                    .onCompleteResponse(response -> {
                        hb.cancel(false);
                        boolean cancelled = CANCEL_FLAGS.remove(sessionId) != null;
                        String full = fullText.toString();
                        if (partialContent != null && !partialContent.isBlank()) {
                            full = partialContent + full;
                        }
                        if (!full.isBlank()) {
                            upsertAssistantMessage(sessionId, partialMsgId[0], full, null, msgSkillScene);
                        }
                        ctx.done = true;
                        ACTIVE_STREAMS.remove(sessionId);
                        cleanupToolStartTimes(sessionId);
                        send(ctx.emitter, cancelled ? "stopped" : "done", "{\"sessionId\":\"" + sessionId + "\"}");
                        safeComplete(ctx.emitter);
                    })
                    .onError(error -> {
                        hb.cancel(false);
                        CANCEL_FLAGS.remove(sessionId);
                        ctx.done = true;
                        ACTIVE_STREAMS.remove(sessionId);
                        cleanupToolStartTimes(sessionId);
                        log.error("[Phase5] Agent 对话异常", error);
                        String full = fullText.toString();
                        if (partialContent != null && !partialContent.isBlank()) {
                            full = partialContent + full;
                        }
                        String errMsg = error.getMessage() != null ? error.getMessage() : "对话异常";
                        // 无条件 upsert：即使内容为空也落库，保留 error_msg 供切走再回来恢复错误状态
                        upsertAssistantMessage(sessionId, partialMsgId[0], full, errMsg, msgSkillScene);
                        sendError(ctx.emitter, errMsg);
                    })
                    .start();
        } catch (Exception e) {
            hb.cancel(false);
            CANCEL_FLAGS.remove(sessionId);
            ctx.done = true;
            ACTIVE_STREAMS.remove(sessionId);
            cleanupToolStartTimes(sessionId);
            log.error("[Phase5] Agent 对话异常", e);
            String errMsg = e.getMessage() != null ? e.getMessage() : "对话异常";
            // 流式启动前/中抛异常也落库：保留 error_msg 供切走再回来恢复错误状态
            try {
                upsertAssistantMessage(sessionId, partialMsgId[0], partialContent != null ? partialContent : "", errMsg, skillScene);
            } catch (Exception persistEx) {
                log.warn("失败状态落库异常: {}", persistEx.getMessage());
            }
            sendError(ctx.emitter, errMsg);
        }
    }

    /** 落库 assistant 消息：继续生成场景更新占位消息，否则新插（防半截+完整重复历史） */
    private void upsertAssistantMessage(Long sessionId, Long messageId, String content, String errorMsg, String skillScene) {
        if (messageId != null) {
            AgentMessage msg = new AgentMessage();
            msg.setId(messageId);
            msg.setContent(content);
            msg.setErrorMsg(errorMsg);
            msg.setSkillScene(skillScene);
            msg.setCreateTime(new Date());
            messageMapper.updateById(msg);
            return;
        }
        saveMessage(sessionId, "assistant", content, null, null, skillScene, errorMsg);
    }

    /** 工具执行完成回调：推 tool_result 事件 + 持久化 tool 消息（tool 开始事件在 beforeToolExecution 已推） */
    private void onToolExecuted(SseEmitter emitter, Long sessionId, ToolExecution exec) {
        try {
            ToolExecutionRequest req = exec.request();
            String toolName = req.name();
            String result = exec.result();
            // SSE 只推状态，不推查询结果内容（避免数据库字段暴露到前端）。
            // 例外：start_case_generation 的结果是不含敏感字段的需求摘要，前端要据此建工作流卡片；
            // ask_case_gen_type / search_apis 的结果是前端卡片数据（类型选择/接口候选列表）；
            // create_test_case 的结果是用例草稿 JSON，前端据此渲染可编辑草稿卡片；
            // start_api_case_generation 的结果是接口摘要，前端据此推送候选卡片并自动唤起生成弹窗
            JSONObject evt = new JSONObject();
            evt.put("tool", toolName);
            if ("start_case_generation".equals(toolName)
                    || "ask_case_gen_type".equals(toolName)
                    || "search_apis".equals(toolName)
                    || "create_test_case".equals(toolName)
                    || "start_api_case_generation".equals(toolName)) {
                evt.put("result", result);
            } else if (isToolError(result)) {
                evt.put("error", true);
            }
            long[] startArr = TOOL_START_TIMES.remove(req.id());
            if (startArr != null) {
                evt.put("duration", System.currentTimeMillis() - startArr[1]);
            }
            send(emitter, "tool_result", evt.toJSONString());
            saveMessage(sessionId, "tool", null, toolName, result);
        } catch (Exception e) {
            log.warn("工具执行回调处理失败: {}", e.getMessage());
        }
    }

    /** 把 AgentToolRegistry 的工具适配为 LangChain4j ToolSpecification + ToolExecutor */
    private Map<ToolSpecification, ToolExecutor> adaptTools(AgentContext ctx) {
        Map<ToolSpecification, ToolExecutor> map = new LinkedHashMap<>();
        for (AgentTool tool : toolRegistry.all()) {
            ToolSpecification spec = ToolSpecification.builder()
                    .name(tool.name())
                    .description(tool.description())
                    .parameters(buildSchema(tool.paramSchema()))
                    .build();
            final AgentTool t = tool;
            ToolExecutor executor = (request, memId) -> {
                // P0-1: 补齐回调线程上下文（langchain4j 工具执行在回调线程，ThreadLocal 为空）
                ProjectContextHolder.setProjectId(ctx.getProjectId());
                com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.setTeamId(ctx.getTeamId());
                try {
                    if (!ctx.hasPermission(t.requiredPermission())) {
                        return "{\"error\":\"无权限调用此工具\"}";
                    }
                    JSONObject args = (request.arguments() != null && !request.arguments().isEmpty())
                            ? JSON.parseObject(request.arguments()) : new JSONObject();
                    return t.execute(ctx, args);
                } finally {
                    ProjectContextHolder.clear();
                    com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.clear();
                }
            };
            map.put(spec, executor);
        }
        return map;
    }

    /** 从 paramSchema JSON 构建 JsonObjectSchema（参数统一为 String，工具内自行转换） */
    private JsonObjectSchema buildSchema(String paramSchemaJson) {
        JsonObjectSchema.Builder b = JsonObjectSchema.builder();
        try {
            JSONObject schema = JSON.parseObject(paramSchemaJson);
            for (String key : schema.keySet()) {
                b.addStringProperty(key, schema.getString(key));
            }
        } catch (Exception ignore) {
            // schema 解析失败时返回空 schema（无参数工具）
        }
        return b.build();
    }

    // ==================== SSE 工具 ====================

    private Long saveMessage(Long sessionId, String role, String content, String toolName, String toolResult) {
        return saveMessage(sessionId, role, content, toolName, toolResult, null);
    }

    private Long saveMessage(Long sessionId, String role, String content, String toolName, String toolResult, String skillScene) {
        return saveMessage(sessionId, role, content, toolName, toolResult, skillScene, null);
    }

    private Long saveMessage(Long sessionId, String role, String content, String toolName, String toolResult, String skillScene, String errorMsg) {
        AgentMessage msg = new AgentMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setToolName(toolName);
        msg.setToolResult(toolResult);
        msg.setSkillScene(skillScene);
        msg.setErrorMsg(errorMsg);
        msg.setCreateTime(new Date());
        messageMapper.insert(msg);
        return msg.getId();
    }

    /** P2-2 清理该 sessionId 的 TOOL_START_TIMES 泄漏条目 */
    private void cleanupToolStartTimes(Long sessionId) {
        TOOL_START_TIMES.entrySet().removeIf(e -> e.getValue()[0] == sessionId);
    }

    /** 工具执行结果是否为错误（JSON error 格式 或 幻觉工具拦截前缀） */
    private boolean isToolError(String result) {
        return result != null && (result.startsWith("{\"error\"") || (result.startsWith("工具 ") && result.contains("本次调用失败")));
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            // delta 事件 JSON 编码，防止 token 中的换行符破坏 SSE 帧格式（\n 会产生不带 data: 前缀的行）
            if ("delta".equals(event)) {
                data = JSON.toJSONString(data);
            }
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            log.debug("SSE 推送失败: {}", e.getMessage());
        }
    }

    private void safeComplete(SseEmitter emitter) {
        try { emitter.complete(); } catch (Exception ignored) {}
    }

    private void sendError(SseEmitter emitter, String msg) {
        send(emitter, "error", msg);
        safeComplete(emitter);
    }

    private void runAsync(Runnable task, SseEmitter emitter) {
        // 捕获请求线程的上下文，传递到异步线程（ProjectContextHolder 是 ThreadLocal，不跨线程）
        Integer projectId = ProjectContextHolder.getProjectId();
        Integer teamId = com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.getTeamId();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            // 在异步线程中恢复上下文
            ProjectContextHolder.setProjectId(projectId);
            com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.setTeamId(teamId);
            try {
                task.run();
            } catch (Exception e) {
                sendError(emitter, e.getMessage());
            } finally {
                ProjectContextHolder.clear();
                com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder.clear();
            }
        });
    }

    @Data
    public static class ChatRequest {
        private Long sessionId;
        private String message;
        private String partialContent;
        /** 快捷指令场景标识（可选）：有值时后端查模板拼入 system prompt */
        private String skillScene;
        /** 重试/重新生成：true 时后端从库里取最后一条 user 消息重跑，清掉之后的失败记录 */
        private Boolean retry;
    }
}
