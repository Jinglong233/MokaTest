package com.mokatest.platform.demos.ai.skill.apicase;

import com.mokatest.platform.demos.ai.gateway.ChatMessage;
import com.mokatest.platform.demos.ai.skill.AiSkill;
import com.mokatest.platform.demos.ai.skill.DraftGenResult;
import com.mokatest.platform.demos.ai.skill.InstructionIntents;
import com.mokatest.platform.demos.ai.skill.SkillContext;
import com.mokatest.platform.demos.ai.skill.SkillRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Skill：AI 生成 API 接口用例（场景四）
 */
@Component
public class GenerateApiCaseSkill implements AiSkill<DraftGenResult<ApiCaseDraftDTO>> {

    public static final String SCENE = "GENERATE_API_CASE";

    private final ApiCaseDraftParser parser = new ApiCaseDraftParser();

    @Override
    public String scene() {
        return SCENE;
    }

    @Override
    public String requiredPermission() {
        return "auto:api:create";
    }

    @Override
    public List<String> contextSourceCodes() {
        return List.of(
                com.mokatest.platform.demos.ai.knowledge.KnowledgeSource.CODE,
                ApiDefinitionSource.CODE,
                SiblingApiSource.CODE,
                ExistingApiCaseSource.CODE);
    }

    @Override
    public List<ChatMessage> buildPrompt(SkillRequest request, SkillContext context, String previousOutput) {
        int count = InstructionIntents.resolveCount(request.getInstruction(), request.getCount(), 5);

        // 策略段（角色/覆盖要求/风格）：二期可走 project_config 项目级覆盖
        String policy = "你是资深接口测试工程师，负责根据接口定义设计高质量的接口测试用例。\n"
                + "要求：\n"
                + "1. 覆盖正常请求、必填缺失、边界值、异常参数、权限/状态等场景；\n"
                + "1.1 用户本轮指令是最高优先级要求：指令指定的主题/数量/侧重点必须严格满足，"
                + "上下文仅作为背景理解，不要偏离指令主题；"
                + "若指令明确要求不要使用某类上下文（如\"不用参考已有用例\"），忽略对应上下文；\n"
                + "2. 未提供的字段默认沿用接口定义，不要随意改变 method/path；\n";

        // 契约段（输出结构）：代码写死，不可覆盖，覆盖会导致解析失败
        String contract = "## 输出契约（严格遵守，不要输出解释文字、不要用 markdown 代码块）\n"
                + "1. 只输出一个 JSON 对象，结构：\n"
                + "{\"test_cases\":[{\"caseName\":\"用例名称\",\"description\":\"场景说明\","
                + "\"requestHeader\":[{\"name\":\"...\",\"value\":\"...\",\"type\":\"...\",\"disabled\":false}],"
                + "\"query\":[],"
                + "\"bodyJson\":\"raw JSON 请求体文本，可空表示沿用接口默认；可使用 @phone() @integer(1,100) @template(id) 等 Mock 表达式\","
                + "\"assertions\":[{\"apiAssertType\":\"STATUS_CODE/BODY/HEADER/RESPONSE_TIME/CUSTOM\","
                + "\"field\":\"status 或 $.code\",\"assertRelationship\":\"EQUALS/NOT_EQUALS/CONTAINS/NOT_CONTAINS/GT/LT/GE/LE/REGULAR\","
                + "\"assertValue\":\"断言值\"}],"
                + "\"extractions\":[{\"type\":\"JSON_PATH/HEADER\",\"expression\":\"表达式\",\"variableName\":\"变量名\"}]}],"
                + "\"uncertainties\":[\"...\"]}\n"
                + "2. requestHeader/query/extractions 可空（空表示沿用接口默认）；\n"
                + "3. 生成用例时必须检查接口定义是否存在关键缺失（必填参数不明确、取值范围/枚举未定义、"
                + "错误码未说明等）：如有，逐条填入 uncertainties，每条说明「缺什么」和「为什么影响测试设计」；"
                + "如无，uncertainties 返回空数组。";

        StringBuilder user = new StringBuilder();
        user.append("请基于以下上下文，为该接口设计 ").append(count).append(" 条测试用例")
                .append("（若用户本轮指令中明确指定了数量，以指令中的数量为准）：\n\n");
        user.append(context.render());
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            user.append("\n## 本会话对话历史\n").append(request.getHistory())
                    .append("（注意：历史中可能包含用户对之前生成结果的修正意见或补充信息，本轮生成必须吸收）\n");
        }
        if (request.getInstruction() != null && !request.getInstruction().isEmpty()) {
            user.append("\n用户本轮指令：").append(request.getInstruction()).append('\n');
        }
        if (previousOutput != null && !previousOutput.isEmpty()) {
            user.append("\n上一轮已生成如下用例，本轮请按额外要求调整/补充，输出本轮完整用例数组：\n")
                    .append(previousOutput).append('\n');
        }
        return List.of(ChatMessage.system(policy + contract), ChatMessage.user(user.toString()));
    }

    @Override
    public DraftGenResult<ApiCaseDraftDTO> parse(String raw) {
        return parser.parse(raw);
    }
}
