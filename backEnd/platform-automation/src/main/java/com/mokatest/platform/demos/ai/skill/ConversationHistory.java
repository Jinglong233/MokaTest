package com.mokatest.platform.demos.ai.skill;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 对话历史摘要构建：把会话前几轮内容压缩为 prompt 可读的上下文，
 * 让模型感知用户的修正（"第2条不对"）与补充信息（"补充：只支持微信支付"）。
 */
public class ConversationHistory {

    /** 最多带入的轮次数（防 prompt 膨胀） */
    private static final int MAX_ROUNDS = 6;
    /** 单轮 AI 输出摘要最大长度 */
    private static final int MAX_OUTPUT_SUMMARY = 300;

    /**
     * 构建历史摘要（排除最后一轮——即当前正在生成的轮次）
     *
     * @param rounds 轮次数组（AiGenerationRecordService.parseRounds 结果）
     * @return 摘要文本；无历史时返回 null
     */
    public static String build(JSONArray rounds) {
        if (rounds == null || rounds.size() <= 1) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, rounds.size() - 1 - MAX_ROUNDS);
        for (int i = start; i < rounds.size() - 1; i++) {
            JSONObject round = rounds.getJSONObject(i);
            if (round == null) {
                continue;
            }
            String instruction = round.getString("instruction");
            if (instruction != null && !instruction.isEmpty()) {
                sb.append("用户：").append(instruction).append('\n');
            }
            String status = round.getString("status");
            String type = round.getString("type");
            if ("qa".equals(type)) {
                String answer = abbreviate(round.getString("rawText"), MAX_OUTPUT_SUMMARY);
                if (answer != null) {
                    sb.append("助手：").append(answer).append('\n');
                }
            } else if ("DONE".equals(status)) {
                JSONArray drafts = round.getJSONArray("drafts");
                if (drafts != null && !drafts.isEmpty()) {
                    sb.append("助手：生成了 ").append(drafts.size()).append(" 条用例草稿（");
                    int limit = Math.min(drafts.size(), 5);
                    for (int j = 0; j < limit; j++) {
                        JSONObject d = drafts.getJSONObject(j);
                        if (d != null && d.getString("caseName") != null) {
                            sb.append(d.getString("caseName"));
                            if (j < limit - 1) {
                                sb.append("、");
                            }
                        }
                    }
                    if (drafts.size() > 5) {
                        sb.append(" 等");
                    }
                    sb.append("）\n");
                }
            } else if ("FAILED".equals(status)) {
                sb.append("助手：上一轮生成失败\n");
            } else if ("STOPPED".equals(status)) {
                sb.append("助手：上一轮被用户手动停止\n");
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String abbreviate(String text, int max) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
