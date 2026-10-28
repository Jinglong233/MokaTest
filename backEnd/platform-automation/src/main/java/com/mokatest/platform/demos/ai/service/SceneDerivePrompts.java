package com.mokatest.platform.demos.ai.service;

/**
 * AI 派生场景（批次二·步骤6）的 prompt 模板与防护上限常量。
 */
public final class SceneDerivePrompts {

    private SceneDerivePrompts() {
    }

    /** 勾选范围内元素总数硬上限，超出直接报错提示缩小范围 */
    public static final int MAX_ELEMENTS = 200;
    /** 业务规则长度上限（字符） */
    public static final int MAX_BIZ_RULES_LENGTH = 2000;
    /** 派生指令长度上限（字符） */
    public static final int MAX_INSTRUCTION_LENGTH = 500;
    /** 覆盖方向条数与单条长度上限 */
    public static final int MAX_COVERAGE_DIRS = 10;
    public static final int MAX_COVERAGE_DIR_LENGTH = 50;
    /** 派生场景步骤数上限 */
    public static final int MAX_STEPS = 30;
    /** 源场景步骤摘要最多带入的条数 */
    public static final int MAX_SOURCE_STEPS = 50;
    /** 元素描述摘要截断长度 */
    public static final int MAX_ELEMENT_DESC = 80;

    /** 「AI派生场景」分组目录名（项目根目录下自动创建） */
    public static final String DERIVE_FOLDER_NAME = "AI派生场景";

    public static String systemPrompt() {
        return """
                你是 UI 自动化测试场景设计专家。用户给你一个已存在的「源场景」和一批「可用元素」，要求你派生出一个新的测试场景草稿。

                硬性规则（必须全部遵守）：
                1. 只输出一个 JSON 对象，不要输出任何解释文字、不要使用 markdown 代码块标记。
                2. 输出结构固定为：{"name":"场景名称","description":"场景描述","steps":[{"stepType":"...","stepName":"...","stepDetail":{...}}]}
                3. 引用元素只能通过 stepDetail 中的 "elementId" 字段，值必须是「可用元素清单」里给出的元素 id（整数）。严禁编造清单之外的 id，严禁输出定位表达式。
                4. 支持的 stepType 与对应 stepDetail 结构：
                   - OPEN_PAGE：{"url":"页面地址"}（若源场景有打开页面步骤，沿用其 url；没有则省略该步骤）
                   - CLICK：{"clickType":"SINGLE_CLICK","elementId":123}（clickType 可选 SINGLE_CLICK / DOUBLE_CLICK / RIGHT_CLICK）
                   - HOVER：{"elementId":123}
                   - KEYBOARD（向元素输入文本）：{"inputType":"NORMAL","inputValue":"要输入的文本","elementId":123}
                   - WAIT：{"waitType":"TIME","waitTime":2}（waitTime 单位秒）
                   - ASSERT（断言元素可见/包含文本）：{"elementId":123,"assertText":"期望文本"}
                   - BACK / FORWARD / REFRESH / CLOSE_PAGE：stepDetail 输出 {}
                5. steps 数量控制在 3~15 个，stepName 用简洁中文描述动作（如「点击 登录按钮」）。
                6. 按用户给定的「覆盖方向」设计步骤（如反用例、边界值、异常路径、权限校验），并遵守「业务规则」中的约束。
                """;
    }

    public static String buildUserMessage(String sourceSceneSummary, String elementSummary,
                                          String coverageDirs, String bizRules, String instruction) {
        StringBuilder sb = new StringBuilder();
        sb.append("【源场景】\n").append(sourceSceneSummary).append("\n\n");
        sb.append("【可用元素清单】（格式：元素id｜元素名称｜描述；只能引用这些 id）\n");
        sb.append(elementSummary == null || elementSummary.isEmpty() ? "（空，本次不允许引用任何元素）" : elementSummary);
        sb.append("\n\n");
        if (coverageDirs != null && !coverageDirs.isEmpty()) {
            sb.append("【覆盖方向】\n").append(coverageDirs).append("\n\n");
        }
        if (bizRules != null && !bizRules.isEmpty()) {
            sb.append("【业务规则】\n").append(bizRules).append("\n\n");
        }
        sb.append("【派生指令】\n");
        sb.append(instruction == null || instruction.isEmpty() ? "基于源场景派生一个补充场景" : instruction);
        return sb.toString();
    }
}
