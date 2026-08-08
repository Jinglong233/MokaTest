package com.mokatest.platform.demos.ai.router;

import com.mokatest.platform.demos.ai.gateway.AiGatewayService;
import com.mokatest.platform.demos.ai.gateway.ChatMessage;
import com.mokatest.platform.demos.ai.gateway.ChatOptions;
import com.mokatest.platform.demos.ai.gateway.ChatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 意图路由器：判断用户本轮指令是「问答讨论」还是「生成/修正用例」
 *
 * 设计（方案 B 单模式智能分流）：
 * - 由模型动态判断意图（轻量分类调用，max_tokens=16，几乎无成本）
 * - 修正生成结果（"第2条不对"）、补充需求信息（"补充：只支持微信支付"）都归为 GENERATE，
 *   由生成侧把对话历史注入 prompt 感知修正
 * - 分类失败一律兜底 GENERATE（生成工具的安全默认），失败原因打日志可观测
 */
@Component
public class AiIntentRouter {

    private static final Logger log = LoggerFactory.getLogger(AiIntentRouter.class);

    public enum Intent { QA, GENERATE }

    @Autowired
    private AiGatewayService gatewayService;

    /**
     * 分类用户意图
     *
     * @param instruction 用户指令
     * @param options     调用选项（scene 复用主场景，userId/teamId/projectId 已填）
     */
    public Intent route(String instruction, ChatOptions options) {
        try {
            String system = "你是意图分类器。用户正在使用一个测试用例生成助手，请判断用户本轮输入的意图：\n"
                    + "- 如果用户要求生成/补充/调整/修正测试用例，或在补充与需求相关的业务信息"
                    + "（例如\"再生成几条\"\"第2条不对，应该是…\"\"补充：支付只支持微信\"），回答 GENERATE\n"
                    + "- 如果用户只是在提问、讨论、咨询、闲聊（例如\"这个需求是干什么的\"\"你是谁\"\"为什么要设计这条用例\"），回答 QA\n"
                    + "只回答 GENERATE 或 QA 一个词，不要思考过程，不要任何解释。";
            ChatOptions classifyOptions = new ChatOptions();
            classifyOptions.setScene(options.getScene());
            classifyOptions.setUserId(options.getUserId());
            classifyOptions.setTeamId(options.getTeamId());
            classifyOptions.setProjectId(options.getProjectId());
            classifyOptions.setPromptSummary("意图分类:" + abbreviate(instruction, 50));
            classifyOptions.setMaxTokens(64);
            classifyOptions.setTemperature(0.0);
            // 轻量分类调用：缩短超时快速失败（兜底 GENERATE），避免阻塞主流的首字节
            classifyOptions.setTimeoutMs(15000);
            ChatResult result = gatewayService.chat(
                    List.of(ChatMessage.system(system), ChatMessage.user(instruction)), classifyOptions);
            String answer = result.getContent() == null ? "" : result.getContent().trim().toUpperCase();
            // 推理模型兜底提取时可能带思考过程，按"最后一个出现的词"判定
            Intent intent;
            int qaIdx = answer.lastIndexOf("QA");
            int genIdx = answer.lastIndexOf("GENERATE");
            if (qaIdx >= 0 && qaIdx > genIdx) {
                intent = Intent.QA;
            } else {
                intent = Intent.GENERATE;
            }
            log.info("意图分类: \"{}\" -> {} (原始回答: {})", abbreviate(instruction, 30), intent, answer);
            return intent;
        } catch (Exception e) {
            log.warn("意图分类失败，按生成处理: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return Intent.GENERATE;
        }
    }

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
