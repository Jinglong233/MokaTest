package com.mokatest.platform.demos.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 意图分类器（AI Service 返回 enum，替代旧 AiIntentRouter 83 行手写分类）
 */
public interface IntentClassifier {

    @SystemMessage("用户正在使用测试用例生成助手，判断用户本轮输入的意图：\n"
            + "- 明确要求生成/补充/调整/修正测试用例，或补充需求信息 → GENERATE\n"
            + "- 提问/讨论/咨询/闲聊/询问已生成数量或当前状态 → QA\n"
            + "注意：'生成了几个'、'有多少用例'、'什么状态'是询问而非指令，属于 QA。\n"
            + "只回答 GENERATE 或 QA。")
    Intent classify(@UserMessage String message);
}
