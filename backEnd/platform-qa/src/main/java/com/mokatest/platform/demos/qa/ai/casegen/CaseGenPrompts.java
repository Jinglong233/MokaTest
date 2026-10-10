package com.mokatest.platform.demos.qa.ai.casegen;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用例生成 prompt 模板 + 指令条数解析。
 *
 * 从旧 GenerateTestCaseSkill / TestPointExtractSkill / InstructionIntents 提炼，
 * 使三个 Skill 类可删除。system prompt 为固定文本（无动态部分），由 AssistantFactory
 * 的 systemMessageProvider 注入 CaseGenerationAssistant。
 */
public final class CaseGenPrompts {

    private CaseGenPrompts() {
    }

    public static final String SCENE_CASE = "GENERATE_CASE";
    public static final String SCENE_POINTS = "EXTRACT_TEST_POINTS";

    /** 条数上限（与前端输入框上限一致） */
    private static final int MAX_COUNT = 20;
    /** "5条" "3个" "10 条用例" "5 cases" 等 */
    private static final Pattern COUNT_PATTERN = Pattern.compile(
            "([0-9]{1,2}|[一二三四五六七八九十两])\\s*(?:条|个|cases?|CASES?)");
    private static final java.util.Map<Character, Integer> CN_NUM = new java.util.HashMap<>();

    static {
        CN_NUM.put('一', 1); CN_NUM.put('二', 2); CN_NUM.put('两', 2);
        CN_NUM.put('三', 3); CN_NUM.put('四', 4); CN_NUM.put('五', 5);
        CN_NUM.put('六', 6); CN_NUM.put('七', 7); CN_NUM.put('八', 8);
        CN_NUM.put('九', 9); CN_NUM.put('十', 10);
    }

    /**
     * 解析生成条数：优先用户指令中明确指定的数量，其次调用方传入值，最后默认值
     */
    public static int resolveCount(String instruction, Integer fallback, int def) {
        if (instruction != null) {
            Matcher m = COUNT_PATTERN.matcher(instruction);
            if (m.find()) {
                String token = m.group(1);
                int n = Character.isDigit(token.charAt(0))
                        ? Integer.parseInt(token)
                        : CN_NUM.getOrDefault(token.charAt(0), 0);
                if (n > 0) {
                    return Math.min(n, MAX_COUNT);
                }
            }
        }
        if (fallback != null && fallback > 0) {
            return Math.min(fallback, MAX_COUNT);
        }
        return def;
    }

    // ==================== 用例生成 ====================

    public static String caseGenSystemPrompt() {
        return "你是资深测试工程师，负责根据需求设计高质量的测试用例。\n"
                + "要求：\n"
                + "0. 生成范围锚定：本会话锚定单一需求，所有用例必须围绕该需求设计；"
                + "若用户指令要求为其他需求/模块生成用例，不要执行，提示用户到对应需求下发起生成。\n"
                + "1. 覆盖正常流程、边界条件、异常场景；\n"
                + "1.1 用户本轮指令是最高优先级要求：指令指定的主题/数量/侧重点必须严格满足，"
                + "上下文仅作为背景理解，不要偏离指令主题去覆盖无关内容；"
                + "若指令明确要求不要使用某类上下文（如\"不用参考历史BUG\"），忽略对应上下文；\n"
                + "1.2 测试设计方法显式化：每条用例必须基于一种设计方法产出并在 designMethod 字段标注。\n"
                + "  - 正常流程用场景法(SCENARIO)；\n"
                + "  - 输入项必须做等价类(EQUIVALENCE)+边界值(BOUNDARY)；\n"
                + "  - 异常路径用错误推测(ERROR_GUESSING)；\n"
                + "1.3 每条用例必须在 coverPoints 字段声明覆盖了哪些设计维度点（TP-x），便于覆盖度可解释。\n"
                + "## 输出契约（严格遵守）\n"
                + "只输出一个 JSON 对象，不要输出任何解释文字，不要用 markdown 代码块包裹。\n"
                + "首轮生成时必须同时产出 test_points 维度清单（5 个分类：功能/边界/异常/权限/安全），后续轮次只需 test_cases。\n"
                + "结构如下：\n"
                + "{\"test_points\":[{\"id\":\"TP-1\",\"title\":\"维度点标题\",\"category\":\"FUNCTION\",\"priority\":\"P0\"}],"
                + "\"test_cases\":[{\"caseName\":\"用例名称\",\"priority\":\"P0\","
                + "\"caseType\":\"FUNCTION\",\"designMethod\":\"SCENARIO\","
                + "\"coverPoints\":[\"TP-1\"],\"preCondition\":\"前置条件\","
                + "\"testSteps\":[{\"step\":\"操作步骤\",\"expected\":\"预期结果\"}],"
                + "\"tags\":\"逗号分隔标签\",\"expectDuration\":3}]}\n"
                + "test_points 字段说明：\n"
                + "- id: 维度点ID（TP-1, TP-2...）\n"
                + "- title: 维度点标题（简明描述要验证什么）\n"
                + "- category: FUNCTION/BOUNDARY/EXCEPTION/PERMISSION/SECURITY 之一\n"
                + "- priority: P0/P1/P2 之一\n"
                + "test_cases 字段说明：\n"
                + "- caseName: 用例名称（简明具体）\n"
                + "- priority: P0/P1/P2 之一\n"
                + "- caseType: FUNCTION/API/PERFORMANCE/COMPATIBILITY/SMOKE 之一\n"
                + "- designMethod: EQUIVALENCE/BOUNDARY/SCENARIO/ERROR_GUESSING 之一\n"
                + "- coverPoints: 覆盖的维度点ID数组（可空数组）\n"
                + "- preCondition: 前置条件\n"
                + "- testSteps: 步骤数组，每项含 step 和 expected\n"
                + "- tags: 逗号分隔标签\n"
                + "- expectDuration: 预期执行时长（分钟）";
    }
    
    /** 提取系统提示词：从 Markdown 用例文档提取结构化 JSON（用于采纳流程） */
    public static String extractionSystemPrompt() {
        return "你是测试用例提取助手。将用户提供的 Markdown 格式用例文档提取为结构化 JSON。\n"
                + "## 输出契约（严格遵守，不要输出任何解释文字、不要用 markdown 代码块）\n"
                + "只输出一个 JSON 对象：\n"
                + "{\"test_cases\":[{\"caseName\":\"用例名称\",\"preCondition\":\"前置条件\","
                + "\"testSteps\":[{\"step\":\"操作步骤\",\"expected\":\"预期结果\"}],"
                + "\"caseType\":\"FUNCTION/API/PERFORMANCE/COMPATIBILITY/SMOKE 之一\","
                + "\"priority\":\"P0/P1/P2 之一\",\"tags\":\"逗号分隔标签，可空\","
                + "\"expectDuration\":5,"
                + "\"designMethod\":\"EQUIVALENCE/BOUNDARY/SCENARIO/ERROR_GUESSING 之一\","
                + "\"coverPoints\":[\"TP-1\",\"TP-3\"]}]\n"
                + "从 <case-draft> 标签的属性提取 name→caseName、priority→priority、type→caseType、"
                + "method→designMethod、covers→coverPoints，从标签内容提取前置条件、测试步骤、预期结果、标签。";
    }

    public static String buildCaseGenUserMessage(String context, String history, String instruction,
                                                 String previousOutput, int count) {
        StringBuilder user = new StringBuilder();
        user.append("请基于以下上下文，为该需求设计 ").append(count).append(" 条测试用例")
                .append("（若用户本轮指令中明确指定了数量，以指令中的数量为准）：\n\n");
        user.append(context);
        if (history != null && !history.isEmpty()) {
            user.append("\n## 本会话对话历史\n").append(history)
                    .append("（注意：历史中可能包含用户对之前生成结果的修正意见或补充的业务信息，本轮生成必须吸收这些修正与补充）\n");
        }
        if (instruction != null && !instruction.isEmpty()) {
            user.append("\n用户本轮指令：").append(instruction).append('\n');
        }
        if (previousOutput != null && !previousOutput.isEmpty()) {
            user.append("\n上一轮已生成如下用例，本轮请在上一轮基础上按额外要求调整/补充，输出本轮完整用例数组：\n")
                    .append(previousOutput).append('\n');
        }
        return user.toString();
    }

    // ==================== 逐点生成（V2 任务制） ====================

    /**
     * 逐点生成系统提示词：在 caseGenSystemPrompt 基础上去掉"首轮产出 test_points"，
     * 增加逐点聚焦约束：只为指定维度点生成 0-5 条用例，不输出 test_points。
     */
    public static String perPointSystemPrompt() {
        return caseGenSystemPrompt()
                .replace("首轮生成时必须同时产出 test_points 维度清单（5 个分类：功能/边界/异常/权限/安全），后续轮次只需 test_cases。\n", "")
                + "\n## 逐点生成约束\n"
                + "本次只为指定的单个维度点生成用例（0-5 条），不要输出 test_points，不要为其他维度点生成用例。\n"
                + "如果该维度点无法生成有意义的用例，输出空 test_cases 数组并在 uncertainties 字段说明原因（不允许静默跳过）。\n"
                + "每条用例的 coverPoints 必须包含该维度点 ID。";
    }

    /**
     * 逐点生成 user message：聚焦单个维度点 + 需求上下文 + 去重清单。
     */
    public static String buildPerPointUserMessage(String tpId, String tpTitle, String tpCategory,
                                                   String context, String dedupList, int count) {
        return buildPerPointUserMessage(tpId, tpTitle, tpCategory, context, dedupList, count, null);
    }

    /**
     * 逐点生成 user message：聚焦单个维度点 + 需求上下文 + 去重清单 + 用户补充说明。
     */
    public static String buildPerPointUserMessage(String tpId, String tpTitle, String tpCategory,
                                                   String context, String dedupList, int count,
                                                   String userSupplement) {
        StringBuilder user = new StringBuilder();
        user.append("请为以下维度点生成 ").append(count).append(" 条测试用例：\n\n");
        user.append("维度点 ID：").append(tpId).append('\n');
        user.append("维度点标题：").append(tpTitle).append('\n');
        user.append("维度点分类：").append(tpCategory).append('\n');
        user.append("\n## 需求上下文\n").append(context).append('\n');
        if (dedupList != null && !dedupList.isEmpty()) {
            user.append("\n## 已有用例名称清单（不要重复）\n").append(dedupList).append('\n');
        }
        if (userSupplement != null && !userSupplement.isBlank()) {
            user.append("\n## 用户补充说明（最高优先级，请综合考虑）\n").append(userSupplement).append('\n');
        }
        return user.toString();
    }

    // ==================== 设计维度点提取 ====================

    public static final String POINTS_SYSTEM_PROMPT = """
            你是资深测试架构师，负责从需求中提取用例设计维度点。

            用例设计维度点是从测试视角对需求进行结构化拆解的覆盖维度，每个维度点对应一类需要覆盖的测试场景。
            必须覆盖以下 5 个类别（category）：
            1. FUNCTION（功能）：需求中明确的核心功能流程
            2. BOUNDARY（边界）：输入范围、数量限制、时间边界等边界条件
            3. EXCEPTION（异常）：错误处理、异常输入、中断恢复等
            4. PERMISSION（权限）：角色权限、数据可见性、操作授权
            5. SECURITY（安全）：注入防护、敏感数据保护、越权访问

            严格按以下 JSON 格式输出，不要输出其他内容：
            {
              "test_points": [
                {"id":"TP-1","title":"维度点标题（简明描述要验证什么）","category":"FUNCTION","priority":"P0","source":"需求原文引用(≤50字)"}
              ]
            }

            要求：
            - 每个维度点必须有明确的 category，不重复
            - priority：P0（核心功能/安全）、P1（重要边界/异常）、P2（次要覆盖）
            - source 引用需求原文片段，≤50字，便于追溯
            - 默认提取 8 个维度点，上限 30 个
            - 标题简明具体，不要笼统（如"登录功能边界测试"而非"功能测试"）
            """;

    public static String buildPointsUserMessage(String context, String instruction) {
        StringBuilder user = new StringBuilder();
        if (context != null && !context.isBlank()) {
            user.append(context).append("\n\n");
        }
        user.append("请从以下需求中提取用例设计维度点：\n\n");
        if (instruction != null && !instruction.isEmpty()) {
            user.append("用户补充指令：").append(instruction).append('\n');
        }
        return user.toString();
    }

    // ==================== QA 问答 ====================

    public static String qaSystemPrompt() {
        return "你是资深测试用例生成助手，只服务于测试用例相关的问题。\n"
                + "如果用户的请求与测试用例无关（如生成代码、写文章、翻译、闲聊等），"
                + "礼貌拒绝并引导用户提出测试用例相关问题。\n"
                + "回答使用中文，简洁专业，可以使用 markdown 列表。\n"
                + "补充约束：\n"
                + "1. 回答数量/状态类问题时，必须以下方「会话状态」统计块中的数据为准，"
                + "不要估算、不要说“无法统计”；\n"
                + "2. 只回答用户当前问题，答完即止；不要主动补充测试建议或测试点，除非用户明确要求；\n"
                + "3. 统计块中没有的信息（如逐条明细），直接说明“可在下方摘要/采纳面板中查看”，不要编造。\n"
                + "不要输出 JSON 用例数组；如果用户的问题其实是在要求生成用例，"
                + "也只需用文字给出设计建议即可。";
    }

    public static String buildQaUserMessage(String context, String history, String instruction, String sessionStats) {
        StringBuilder user = new StringBuilder();
        if (sessionStats != null && !sessionStats.isBlank()) {
            user.append(sessionStats).append('\n');
        }
        user.append(context);
        if (history != null && !history.isEmpty()) {
            user.append("\n## 本会话对话历史\n").append(history).append('\n');
        }
        user.append("\n用户问题：").append(instruction);
        return user.toString();
    }

    // ==================== 覆盖审查 ====================
    // prompt 已移至 CoverageAuditAssistant.SYSTEM_PROMPT（§5.2 LangChain4j AI Service 声明式 @SystemMessage）
    // 旧 auditCoverageSystemPrompt() 已废弃删除，含 overallVerdict/overallSummary + 判定口径

    /**
     * 对话历史摘要构建（从旧 ConversationHistory 提炼）：把会话前几轮压缩为 prompt 可读文本。
     * rounds 为 List<Map>（来自 AiGenerationRecordService.parseRounds，Jackson 解析）。
     */
    public static String buildHistory(java.util.List<java.util.Map<String, Object>> rounds) {
        if (rounds == null || rounds.isEmpty()) {
            return null;
        }
        int maxRounds = 6;
        int maxOutputSummary = 300;
        if (rounds.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, rounds.size() - maxRounds);
        for (int i = start; i < rounds.size(); i++) {
            java.util.Map<String, Object> round = rounds.get(i);
            if (round == null) {
                continue;
            }
            String instruction = str(round, "instruction");
            if (instruction != null && !instruction.isEmpty()) {
                sb.append("用户：").append(instruction).append('\n');
            }
            String status = str(round, "status");
            String type = str(round, "type");
            if ("qa".equals(type)) {
                String answer = abbreviate(str(round, "rawText"), maxOutputSummary);
                if (answer != null) {
                    sb.append("助手：").append(answer).append('\n');
                }
            } else if ("DONE".equals(status)) {
                Object draftsObj = round.get("drafts");
                java.util.List<?> drafts = draftsObj instanceof java.util.List ? (java.util.List<?>) draftsObj : null;
                if (drafts != null && !drafts.isEmpty()) {
                    sb.append("助手：生成了 ").append(drafts.size()).append(" 条用例草稿（");
                    int limit = Math.min(drafts.size(), 5);
                    for (int j = 0; j < limit; j++) {
                        Object o = drafts.get(j);
                        String name = o instanceof java.util.Map ? str((java.util.Map<String, Object>) o, "caseName") : null;
                        if (name != null) {
                            sb.append(name);
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

    private static String str(java.util.Map<String, Object> m, String k) {
        if (m == null) return null;
        Object v = m.get(k);
        return v == null ? null : String.valueOf(v);
    }

    private static String abbreviate(String text, int max) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
