package com.mokatest.platform.demos.qa.ai.agent;

import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;

/**
 * 工具：询问用例生成类型（意图分流卡片触发）
 *
 * Agent 识别到用户想生成测试用例但未说明类型时调用，
 * 返回固定标记供前端推送类型选择卡片（功能用例/接口用例/UI 自动化用例）。
 */
@Component
public class AskCaseGenTypeTool implements AgentTool {

    @Override public String name() { return "ask_case_gen_type"; }
    @Override public String description() { return "当用户想生成测试用例但未说明是功能用例（基于需求）还是接口用例（基于API接口）时调用，让用户先选择要生成的用例类型。"
            + "若用户已明确类型（如\"给登录接口生成用例\"\"为XX需求生成用例\"），不要调用本工具。"; }
    @Override public String paramSchema() { return "{}"; }
    @Override public String requiredPermission() { return "qa:ai:use"; }

    @Override
    public String execute(AgentContext ctx, JSONObject args) {
        return "{\"type\": \"case_gen_type_select\"}";
    }
}
