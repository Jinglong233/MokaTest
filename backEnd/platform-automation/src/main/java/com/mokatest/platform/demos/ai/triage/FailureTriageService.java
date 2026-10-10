package com.mokatest.platform.demos.ai.triage;

import com.mokatest.platform.demos.ai.config.ModelManager;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.BaseStepResult;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 失败分诊服务（langchain4j-agentic：sequence(路由 → 条件分支专家)）。
 *
 * 调用时机：步骤 catch 阶段（浏览器还活着时），调试/计划执行共用。
 * 定位类失败不走这里（AiLocatorHealService 快速通道优先）；AI 未配置/异常时返回 null，
 * 调用方按原失败逻辑收尾（等价于未接入分诊）。
 */
@Component
public class FailureTriageService {

    private static final Logger log = LoggerFactory.getLogger(FailureTriageService.class);

    /** 分诊产物：类别 + 专家诊断结论 + BUG 草稿（仅 PRODUCT_BUG） */
    public record TriageOutcome(String category, String detail, BugDraft bugDraft) {
    }

    @Autowired
    private ModelManager modelManager;

    /**
     * 对失败步骤做分诊。任何异常都返回 null（分诊是加分项，绝不反过来影响失败收尾）。
     */
    public TriageOutcome triage(TestExecutionContext ctx, String stepName, String stepType, Exception error) {
        try {
            String input = buildInput(ctx, stepName, stepType, error);

            TriageRouter router = AgenticServices.agentBuilder(TriageRouter.class)
                    .chatModel(modelManager.createDeterministicChatModel("FAILURE_TRIAGE"))
                    .build();

            DataDiagnosisExpert dataExpert = AgenticServices.agentBuilder(DataDiagnosisExpert.class)
                    .chatModel(new com.mokatest.platform.demos.ai.config.SceneStampingChatModel(
                            modelManager.getChatModel(), "FAILURE_TRIAGE"))
                    .build();
            EnvDiagnosisExpert envExpert = AgenticServices.agentBuilder(EnvDiagnosisExpert.class)
                    .chatModel(new com.mokatest.platform.demos.ai.config.SceneStampingChatModel(
                            modelManager.getChatModel(), "FAILURE_TRIAGE"))
                    .build();
            ProductBugExpert bugExpert = AgenticServices.agentBuilder(ProductBugExpert.class)
                    .chatModel(modelManager.createDeterministicChatModel("FAILURE_TRIAGE"))
                    .build();

            UntypedAgent conditional = AgenticServices.conditionalBuilder()
                    .subAgents(scope -> "DATA".equals(scope.readState("category")), dataExpert)
                    .subAgents(scope -> "ENV".equals(scope.readState("category")), envExpert)
                    .subAgents(scope -> "PRODUCT_BUG".equals(scope.readState("category")), bugExpert)
                    .build();

            UntypedAgent workflow = AgenticServices.sequenceBuilder()
                    .subAgents(router, conditional)
                    .output(scope -> {
                        String category = String.valueOf(scope.readState("category", ""));
                        Object diagnosis = scope.readState("diagnosis");
                        Object bugDraft = scope.readState("bugDraft");
                        String detail = diagnosis != null ? String.valueOf(diagnosis)
                                : bugDraft != null ? "疑似产品缺陷，已生成 BUG 草稿" : "";
                        return new TriageOutcome(category, detail,
                                bugDraft instanceof BugDraft bd ? bd : null);
                    })
                    .build();

            Object result = workflow.invoke(Map.of("input", input));
            if (result instanceof TriageOutcome outcome && outcome.category() != null
                    && !"null".equals(outcome.category()) && !outcome.category().isBlank()) {
                log.info("[Triage] 步骤[{}] 分诊结果: {} | {}", stepName, outcome.category(), outcome.detail());
                return outcome;
            }
            return null;
        } catch (Exception e) {
            log.warn("[Triage] 步骤[{}] 分诊异常，按原失败处理: {}", stepName, e.getMessage());
            return null;
        }
    }

    /** 组装分诊输入：步骤信息 + 错误 + 上下文快照（各专家共用一份，按需自取） */
    private String buildInput(TestExecutionContext ctx, String stepName, String stepType, Exception error) {
        StringBuilder sb = new StringBuilder();
        sb.append("步骤：").append(stepName).append("（类型 ").append(stepType).append("）\n");
        String msg = error != null && error.getMessage() != null ? error.getMessage() : "无异常消息";
        sb.append("错误信息：").append(truncate(msg, 1500)).append('\n');
        try {
            BaseStepResult r = ctx.getCurrentCommonStepResult();
            if (r != null) {
                if (r.getPageUrl() != null) sb.append("页面 URL：").append(r.getPageUrl()).append('\n');
                if (r.getApiResponse() != null) {
                    sb.append("接口响应：").append(truncate(String.valueOf(r.getApiResponse()), 500)).append('\n');
                }
                if (r.getExtractResults() != null && !r.getExtractResults().isEmpty()) {
                    sb.append("提取结果：").append(truncate(String.valueOf(r.getExtractResults()), 500)).append('\n');
                }
            }
            if (ctx.getVariables() != null && !ctx.getVariables().isEmpty()) {
                sb.append("变量状态：").append(truncate(String.valueOf(ctx.getVariables()), 800)).append('\n');
            }
            if (ctx.getSceneEnvironment() != null) {
                sb.append("环境：").append(ctx.getSceneEnvironment().getEnvName());
                if (ctx.getSceneEnvironment().getServe() != null) {
                    sb.append("，服务地址：").append(truncate(String.valueOf(ctx.getSceneEnvironment().getServe()), 300));
                }
                sb.append('\n');
            }
        } catch (Exception e) {
            log.debug("[Triage] 上下文快照采集部分失败: {}", e.getMessage());
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
