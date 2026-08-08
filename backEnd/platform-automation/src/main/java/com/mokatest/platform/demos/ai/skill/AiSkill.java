package com.mokatest.platform.demos.ai.skill;

import java.util.List;

/**
 * AI Skill 抽象：一个可复用的 AI 能力单元
 *
 * 组成：prompt 模板 + 上下文来源 + 输出 schema + 解析器 + 权限声明。
 * 系统级内置（一期）；项目级通过 project_config 覆盖（二期演进）。
 *
 * 实现注册为 Spring Bean 后由 SkillRegistry 自动收集。
 */
public interface AiSkill<T> {

    /**
     * 场景标识（与 ai_generation_record.scene / ai_usage_log.scene 对应）
     * 如 GENERATE_CASE / GENERATE_API_CASE / REPORT_SUMMARY / BUG_CLUSTER_INSIGHT
     */
    String scene();

    /**
     * 执行所需权限编码（后端强制校验）
     */
    String requiredPermission();

    /**
     * 该 Skill 使用的上下文来源 code 列表（按层级排序）
     */
    List<String> contextSourceCodes();

    /**
     * 组装 prompt（messages：system + user）
     *
     * @param request 生成请求
     * @param context 已裁剪的上下文
     * @param previousOutput 追加生成时的上一轮输出（首轮为 null）
     */
    List<com.mokatest.platform.demos.ai.gateway.ChatMessage> buildPrompt(
            SkillRequest request, SkillContext context, String previousOutput);

    /**
     * 解析模型原始输出为结构化结果
     *
     * @param raw 模型输出文本
     * @throws com.mokatest.platform.demos.exception.BusinessException 解析失败（调用方可重试一次）
     */
    T parse(String raw);
}
