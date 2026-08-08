package com.mokatest.platform.demos.qa.ai.casegen;

import com.mokatest.platform.demos.ai.gateway.ChatMessage;
import com.mokatest.platform.demos.ai.service.AiConfigService;
import com.mokatest.platform.demos.ai.skill.AiSkill;
import com.mokatest.platform.demos.ai.skill.InstructionIntents;
import com.mokatest.platform.demos.ai.skill.SkillContext;
import com.mokatest.platform.demos.ai.skill.SkillRequest;
import com.mokatest.platform.demos.qa.ai.context.ExistingCaseSource;
import com.mokatest.platform.demos.qa.ai.context.HistoryBugSource;
import com.mokatest.platform.demos.qa.ai.context.ModuleRequirementSource;
import com.mokatest.platform.demos.qa.ai.context.RelatedRequirementSource;
import com.mokatest.platform.demos.qa.ai.context.RequirementCoreSource;
import com.mokatest.platform.demos.qa.ai.util.RichTextCleaner;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.mapper.RequirementMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill：AI 生成测试用例（场景一）
 */
@Component
public class GenerateTestCaseSkill implements AiSkill<com.mokatest.platform.demos.ai.skill.DraftGenResult<CaseDraftDTO>> {

    public static final String SCENE = "GENERATE_CASE";

    @Autowired
    private RequirementMapper requirementMapper;

    @Autowired
    private AiConfigService aiConfigService;

    private final CaseDraftParser parser = new CaseDraftParser();

    @Override
    public String scene() {
        return SCENE;
    }

    @Override
    public String requiredPermission() {
        return "qa:testcase:create";
    }

    @Override
    public List<String> contextSourceCodes() {
        return List.of(
                com.mokatest.platform.demos.ai.knowledge.KnowledgeSource.CODE,
                RequirementCoreSource.CODE,
                RelatedRequirementSource.CODE,
                ModuleRequirementSource.CODE,
                ExistingCaseSource.CODE,
                HistoryBugSource.CODE);
    }

    @Override
    public List<ChatMessage> buildPrompt(SkillRequest request, SkillContext context, String previousOutput) {
        int count = InstructionIntents.resolveCount(request.getInstruction(), request.getCount(), 5);

        // 策略段（角色/覆盖要求/风格）：二期可走 project_config 项目级覆盖
        StringBuilder policy = new StringBuilder();
        policy.append("你是资深测试工程师，负责根据需求设计高质量的测试用例。\n");
        policy.append("要求：\n");
        policy.append("1. 覆盖正常流程、边界条件、异常场景；\n");
        policy.append("1.1 用户本轮指令是最高优先级要求：指令指定的主题/数量/侧重点必须严格满足，"
                + "上下文仅作为背景理解，不要偏离指令主题去覆盖无关内容；"
                + "若指令明确要求不要使用某类上下文（如\"不用参考历史BUG\"），忽略对应上下文；\n");

        // 契约段（输出结构）：代码写死，不可覆盖，覆盖会导致解析失败
        StringBuilder contract = new StringBuilder();
        contract.append("## 输出契约（严格遵守，不要输出任何解释文字、不要用 markdown 代码块）\n");
        contract.append("1. 只输出一个 JSON 对象，结构：\n");
        contract.append("{\"test_cases\":[{\"caseName\":\"用例名称\",\"preCondition\":\"前置条件\",")
                .append("\"testSteps\":[{\"step\":\"操作步骤\",\"expected\":\"预期结果\"}],")
                .append("\"caseType\":\"FUNCTION/API/PERFORMANCE/COMPATIBILITY/SMOKE 之一\",")
                .append("\"priority\":\"P0/P1/P2 之一\",\"tags\":\"逗号分隔标签，可空\",")
                .append("\"expectDuration\":5}],\"uncertainties\":[\"...\"]}\n");
        contract.append("2. 用例名称简明具体，步骤可执行、预期可验证；\n");
        contract.append("3. 生成用例时必须检查需求是否存在关键缺失（数值阈值未指定、角色/枚举未列全、"
                + "边界条件模糊等）：如有，逐条填入 uncertainties，每条说明「缺什么」和「为什么影响测试设计」；"
                + "如无，uncertainties 返回空数组。");

        StringBuilder user = new StringBuilder();
        user.append("请基于以下上下文，为该需求设计 ").append(count).append(" 条测试用例")
                .append("（若用户本轮指令中明确指定了数量，以指令中的数量为准）：\n\n");
        user.append(context.render());
        if (request.getHistory() != null && !request.getHistory().isEmpty()) {
            user.append("\n## 本会话对话历史\n").append(request.getHistory())
                    .append("（注意：历史中可能包含用户对之前生成结果的修正意见或补充的业务信息，本轮生成必须吸收这些修正与补充）\n");
        }
        if (request.getInstruction() != null && !request.getInstruction().isEmpty()) {
            user.append("\n用户本轮指令：").append(request.getInstruction()).append('\n');
        }
        if (previousOutput != null && !previousOutput.isEmpty()) {
            user.append("\n上一轮已生成如下用例，本轮请在上一轮基础上按额外要求调整/补充，输出本轮完整用例数组：\n")
                    .append(previousOutput).append('\n');
        }

        // 多模态：开启时把需求描述中的图片一并发送
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(policy.toString() + contract));
        List<String> imageUrls = visionImageUrls(request);
        if (!imageUrls.isEmpty()) {
            messages.add(ChatMessage.userWithImages(user.toString(), imageUrls));
        } else {
            messages.add(ChatMessage.user(user.toString()));
        }
        return messages;
    }

    @Override
    public com.mokatest.platform.demos.ai.skill.DraftGenResult<CaseDraftDTO> parse(String raw) {
        return parser.parse(raw);
    }

    /** 多模态开启时提取需求描述中的图片地址（最多 4 张） */
    private List<String> visionImageUrls(SkillRequest request) {
        if (!aiConfigService.isVisionEnabled()) {
            return List.of();
        }
        try {
            Requirement req = requirementMapper.selectOne(new QueryWrapper<Requirement>()
                    .eq("id", request.getEntityId())
                    .eq("project_id", request.getProjectId())
                    .select("id", "description"));
            if (req == null || req.getDescription() == null) {
                return List.of();
            }
            List<String> urls = RichTextCleaner.extractImageUrls(req.getDescription());
            return urls.size() > 4 ? urls.subList(0, 4) : urls;
        } catch (Exception e) {
            return List.of();
        }
    }
}
