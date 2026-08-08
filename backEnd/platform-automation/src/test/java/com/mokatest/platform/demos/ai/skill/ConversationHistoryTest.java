package com.mokatest.platform.demos.ai.skill;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对话历史摘要构建单元测试
 */
class ConversationHistoryTest {

    private JSONObject round(int no, String type, String instruction, String status, String rawText, int draftCount) {
        JSONObject r = new JSONObject();
        r.put("round", no);
        r.put("type", type);
        r.put("instruction", instruction);
        r.put("status", status);
        r.put("rawText", rawText);
        JSONArray drafts = new JSONArray();
        for (int i = 0; i < draftCount; i++) {
            JSONObject d = new JSONObject();
            d.put("caseName", "用例" + (i + 1));
            drafts.add(d);
        }
        r.put("drafts", drafts);
        return r;
    }

    @Test
    void emptyOrSingleRoundReturnsNull() {
        assertNull(ConversationHistory.build(null));
        assertNull(ConversationHistory.build(new JSONArray()));
        JSONArray single = new JSONArray();
        single.add(round(1, "gen", "生成5条", "GENERATING", null, 0));
        assertNull(ConversationHistory.build(single), "只有当前轮时无历史");
    }

    @Test
    void genRoundsSummarized() {
        JSONArray rounds = new JSONArray();
        rounds.add(round(1, "gen", "给我5条边界用例", "DONE", null, 3));
        rounds.add(round(2, "gen", "第2条不对", "GENERATING", null, 0)); // 当前轮排除
        String history = ConversationHistory.build(rounds);
        assertNotNull(history);
        assertTrue(history.contains("用户：给我5条边界用例"));
        assertTrue(history.contains("生成了 3 条用例草稿"));
        assertTrue(history.contains("用例1"));
        assertFalse(history.contains("第2条不对"), "当前轮不计入历史");
    }

    @Test
    void qaRoundsIncluded() {
        JSONArray rounds = new JSONArray();
        rounds.add(round(1, "qa", "这个需求是干什么的", "DONE", "这是登录功能", 0));
        rounds.add(round(2, "gen", "那生成用例吧", "GENERATING", null, 0));
        String history = ConversationHistory.build(rounds);
        assertTrue(history.contains("助手：这是登录功能"));
    }

    @Test
    void failedAndStoppedMarked() {
        JSONArray rounds = new JSONArray();
        rounds.add(round(1, "gen", "生成", "FAILED", null, 0));
        rounds.add(round(2, "gen", "再来", "STOPPED", null, 0));
        rounds.add(round(3, "gen", "继续", "GENERATING", null, 0));
        String history = ConversationHistory.build(rounds);
        assertTrue(history.contains("上一轮生成失败"));
        assertTrue(history.contains("被用户手动停止"));
    }

    @Test
    void limitsToRecentSixRounds() {
        JSONArray rounds = new JSONArray();
        for (int i = 1; i <= 9; i++) {
            rounds.add(round(i, "gen", "指令" + i, "DONE", null, 1));
        }
        String history = ConversationHistory.build(rounds);
        assertFalse(history.contains("指令1"), "过早轮次被丢弃");
        assertFalse(history.contains("指令9"), "当前轮排除");
        assertTrue(history.contains("指令8"));
    }
}
