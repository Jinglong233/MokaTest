package com.mokatest.platform.demos.ai.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 需求评审检查表常量与工具方法。
 *
 * 从旧 RequirementReviewSkill 提取，供 AiReviewController / AiCaseService / TestPointExtractSkill 使用。
 * RequirementReviewSkill 已删除，评审执行改由 RequirementReviewAssistant（LangChain4j AI Service）处理。
 */
public final class ReviewChecklist {

    private ReviewChecklist() {}

    /** 30 项检查表 ID */
    public static final List<String> CHECKLIST_IDS = List.of(
            "CMP-1", "CMP-2", "CMP-3", "CMP-4", "CMP-5",
            "CLR-1", "CLR-2", "CLR-3", "CLR-4", "CLR-5",
            "TST-1", "TST-2", "TST-3", "TST-4", "TST-5",
            "CON-1", "CON-2", "CON-3", "CON-4", "CON-5",
            "SEC-1", "SEC-2", "SEC-3", "SEC-4", "SEC-5",
            "BND-1", "BND-2", "BND-3", "BND-4", "BND-5"
    );

    public static final Map<String, String> DIM_LABELS = new LinkedHashMap<>();
    public static final Map<String, List<String>> DIM_ITEMS = new LinkedHashMap<>();
    public static final Map<String, String> ITEM_TEXTS = new LinkedHashMap<>();

    static {
        DIM_LABELS.put("completeness", "完整度");
        DIM_LABELS.put("clarity", "清晰度");
        DIM_LABELS.put("testability", "可测性");
        DIM_LABELS.put("consistency", "一致性");
        DIM_LABELS.put("security", "安全性");
        DIM_LABELS.put("boundary", "边界覆盖");

        DIM_ITEMS.put("completeness", List.of("CMP-1", "CMP-2", "CMP-3", "CMP-4", "CMP-5"));
        DIM_ITEMS.put("clarity", List.of("CLR-1", "CLR-2", "CLR-3", "CLR-4", "CLR-5"));
        DIM_ITEMS.put("testability", List.of("TST-1", "TST-2", "TST-3", "TST-4", "TST-5"));
        DIM_ITEMS.put("consistency", List.of("CON-1", "CON-2", "CON-3", "CON-4", "CON-5"));
        DIM_ITEMS.put("security", List.of("SEC-1", "SEC-2", "SEC-3", "SEC-4", "SEC-5"));
        DIM_ITEMS.put("boundary", List.of("BND-1", "BND-2", "BND-3", "BND-4", "BND-5"));

        ITEM_TEXTS.put("CMP-1", "是否描述了正常主流程（核心路径）");
        ITEM_TEXTS.put("CMP-2", "是否描述了异常/失败流程（校验失败、网络异常等）");
        ITEM_TEXTS.put("CMP-3", "是否说明了前置条件与依赖");
        ITEM_TEXTS.put("CMP-4", "是否说明了操作完成后的结果/后置状态");
        ITEM_TEXTS.put("CMP-5", "是否覆盖了不同角色/状态下的使用场景");
        ITEM_TEXTS.put("CLR-1", "关键术语是否有明确定义且全文一致");
        ITEM_TEXTS.put("CLR-2", "数值/阈值是否给出具体数字（而非\"较快\"\"大量\"等模糊词）");
        ITEM_TEXTS.put("CLR-3", "界面/交互描述是否可对应到具体元素");
        ITEM_TEXTS.put("CLR-4", "是否存在歧义表述（一词多义、指代不明）");
        ITEM_TEXTS.put("CLR-5", "需求范围边界（做什么/不做什么）是否明确");
        ITEM_TEXTS.put("TST-1", "是否有明确的验收标准");
        ITEM_TEXTS.put("TST-2", "验收标准是否可量化、可判定通过/失败");
        ITEM_TEXTS.put("TST-3", "每条关键业务规则是否可通过输入-输出验证");
        ITEM_TEXTS.put("TST-4", "是否说明性能/容量等可度量指标");
        ITEM_TEXTS.put("TST-5", "结果是否可通过界面或接口观察到");
        ITEM_TEXTS.put("CON-1", "需求内部前后描述是否矛盾");
        ITEM_TEXTS.put("CON-2", "与文中提及的其他需求/模块规则是否冲突");
        ITEM_TEXTS.put("CON-3", "数据口径（字段/单位/格式）是否前后一致");
        ITEM_TEXTS.put("CON-4", "流程描述与规则描述是否一致");
        ITEM_TEXTS.put("CON-5", "术语与平台既有概念是否一致");
        ITEM_TEXTS.put("SEC-1", "是否说明权限控制（谁能查看/谁能操作）");
        ITEM_TEXTS.put("SEC-2", "是否涉及敏感数据的展示/传输保护");
        ITEM_TEXTS.put("SEC-3", "是否考虑越权/未登录等非法访问场景");
        ITEM_TEXTS.put("SEC-4", "是否说明操作审计/日志要求");
        ITEM_TEXTS.put("SEC-5", "是否考虑输入合法性校验");
        ITEM_TEXTS.put("BND-1", "是否说明输入边界（空值/超长/非法格式）");
        ITEM_TEXTS.put("BND-2", "是否考虑并发/重复提交");
        ITEM_TEXTS.put("BND-3", "是否考虑超时/失败重试");
        ITEM_TEXTS.put("BND-4", "是否说明数据量上限/分页");
        ITEM_TEXTS.put("BND-5", "是否考虑异常后的恢复/补偿");
    }

    /** 渲染提示词用的检查表全文（### 维度（key）+ 逐条 id: 文案），单一数据源来自上面的 Map */
    public static String renderChecklistSpec() {
        StringBuilder sb = new StringBuilder();
        DIM_LABELS.forEach((dim, label) -> {
            sb.append("### ").append(label).append('（').append(dim).append('）').append('\n');
            for (String id : DIM_ITEMS.get(dim)) {
                sb.append(id).append(": ").append(ITEM_TEXTS.get(id)).append('\n');
            }
            sb.append('\n');
        });
        return sb.toString();
    }

    /** 渲染指定维度子集的检查表（并行评审专家各管一段时使用） */
    public static String renderChecklistSpec(java.util.Collection<String> dims) {
        StringBuilder sb = new StringBuilder();
        for (String dim : dims) {
            String label = DIM_LABELS.get(dim);
            if (label == null) continue;
            sb.append("### ").append(label).append('（').append(dim).append('）').append('\n');
            for (String id : DIM_ITEMS.get(dim)) {
                sb.append(id).append(": ").append(ITEM_TEXTS.get(id)).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 指定维度子集包含的全部检查项 id */
    public static List<String> idsOfDims(java.util.Collection<String> dims) {
        List<String> ids = new ArrayList<>();
        for (String dim : dims) {
            List<String> items = DIM_ITEMS.get(dim);
            if (items != null) ids.addAll(items);
        }
        return ids;
    }

    public static String dimOfId(String id) {
        if (id == null) return null;
        if (id.startsWith("CMP")) return "completeness";
        if (id.startsWith("CLR")) return "clarity";
        if (id.startsWith("TST")) return "testability";
        if (id.startsWith("CON")) return "consistency";
        if (id.startsWith("SEC")) return "security";
        if (id.startsWith("BND")) return "boundary";
        return null;
    }

    public static String extractJson(String raw) {
        if (raw == null) return "{}";
        String text = raw.trim();
        if (text.startsWith("```")) {
            int start = text.indexOf('\n');
            int end = text.lastIndexOf("```");
            if (start > 0 && end > start) {
                text = text.substring(start + 1, end).trim();
            }
        }
        int first = text.indexOf('{');
        if (first < 0) return text;
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = first; i < text.length(); i++) {
            char c = text.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\' && inStr) { esc = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (inStr) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(first, i + 1);
                }
            }
        }
        int last = Math.max(text.lastIndexOf('}'), text.lastIndexOf(']'));
        if (last > first) {
            return closeOpenBrackets(text.substring(first, last + 1));
        }
        return text;
    }

    private static String closeOpenBrackets(String text) {
        java.util.Deque<Character> stack = new java.util.ArrayDeque<>();
        boolean inStr = false;
        boolean esc = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\' && inStr) { esc = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (inStr) continue;
            if (c == '{' || c == '[') stack.push(c);
            else if (c == '}' || c == ']') { if (!stack.isEmpty()) stack.pop(); }
        }
        StringBuilder sb = new StringBuilder(text);
        while (!stack.isEmpty()) {
            sb.append(stack.pop() == '{' ? '}' : ']');
        }
        return sb.toString();
    }
}
