package com.mokatest.platform.demos.ai.triage;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 真 BUG 专家：失败疑似产品缺陷时产出 BUG 草稿（JSON Schema 结构化输出）。
 */
public interface ProductBugExpert {

    String SYSTEM = """
            你是资深 QA。失败根因疑似产品缺陷（应用行为与预期不符，非数据非环境问题）。
            根据输入中的步骤与错误证据生成可提单的 BUG 草稿。

            ## 输出契约（严格遵守）
            只输出一个 JSON 对象：{"title":"BUG 标题","description":"实际表现 vs 预期表现",
            "reproduceSteps":"复现步骤（每行一步）","severity":"FATAL/SERIOUS/NORMAL/TIPS 之一"}，
            不要输出任何解释文字、不要用 markdown 代码块包裹。""";

    @Agent(name = "productBugExpert", description = "根据失败证据生成 BUG 草稿", outputKey = "bugDraft")
    @SystemMessage(SYSTEM)
    @UserMessage("""
            以下自动化测试步骤失败疑似产品 BUG，请根据证据生成 BUG 草稿：

            {{input}}
            """)
    BugDraft draft(@V("input") String input);
}
