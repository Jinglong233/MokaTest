package com.mokatest.platform.demos.ai.skill;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.domain.AiSkillDefinition;
import com.mokatest.platform.demos.ai.guardrail.PromptInjectionGuard;
import com.mokatest.platform.demos.ai.mapper.AiSkillDefinitionMapper;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * 自定义 Skill 定义管理（项目级 CRUD）
 *
 * 权限：qa:ai:use（复用 AI 工作台权限，项目管理员可操作）
 *
 * 越权防护：projectId 一律取自服务端上下文（ProjectContextHolder），不接受前端传值；
 * 内容防护：Skill prompt 会注入到项目内所有使用者的会话，创建/更新时做注入黑名单
 * 与长度校验，防止 Skill 作者构造破甲指令影响他人。
 */
@Slf4j
@RestController
@RequestMapping("/ai/skill-def")
@RequiredArgsConstructor
@SaCheckPermission("qa:ai:use")
public class SkillDefinitionController {

    private static final int MAX_NAME_LEN = 50;
    private static final int MAX_DESC_LEN = 500;
    private static final int MAX_PROMPT_LEN = 4000;

    private final AiSkillDefinitionMapper mapper;

    @GetMapping("/list")
    public SaResult list() {
        Integer projectId = ProjectContextHolder.getProjectId();
        List<AiSkillDefinition> list = mapper.selectList(
                new QueryWrapper<AiSkillDefinition>()
                        .eq("project_id", projectId)
                        .orderByDesc("update_time"));
        return SaResult.ok().setData(list);
    }

    @PostMapping("/")
    public SaResult create(@RequestBody AiSkillDefinition body) {
        Integer projectId = ProjectContextHolder.getProjectId();
        SaResult check = validateContent(body.getName(), body.getDescription(), body.getSystemPrompt(), true);
        if (check != null) {
            return check;
        }
        body.setProjectId(projectId);
        body.setCreateBy(StpUtil.getLoginIdAsInt());
        body.setCreateTime(new Date());
        body.setUpdateTime(new Date());
        body.setEnabled(1);
        if (body.getToolsEnabled() == null) body.setToolsEnabled(1);
        body.setTemperature(java.math.BigDecimal.valueOf(0.40));
        body.setMaxTokens(4000);
        // scene 后端自动生成：sk_ + 自增id（先插临时值再回填）
        body.setScene("sk_temp");
        mapper.insert(body);
        body.setScene("sk_" + body.getId());
        mapper.updateById(body);
        return SaResult.ok("创建成功").setData(body);
    }

    @PutMapping("/{id}")
    public SaResult update(@PathVariable Long id, @RequestBody AiSkillDefinition body) {
        Integer projectId = ProjectContextHolder.getProjectId();
        AiSkillDefinition existing = mapper.selectById(id);
        if (existing == null || !existing.getProjectId().equals(projectId)) {
            return SaResult.error("Skill 不存在或无权访问");
        }
        SaResult check = validateContent(body.getName(), body.getDescription(), body.getSystemPrompt(), true);
        if (check != null) {
            return check;
        }
        existing.setName(body.getName());
        existing.setDescription(body.getDescription());
        existing.setSystemPrompt(body.getSystemPrompt());
        if (body.getToolsEnabled() != null) existing.setToolsEnabled(body.getToolsEnabled());
        existing.setUpdateTime(new Date());
        mapper.updateById(existing);
        return SaResult.ok("更新成功");
    }

    @DeleteMapping("/{id}")
    public SaResult delete(@PathVariable Long id) {
        Integer projectId = ProjectContextHolder.getProjectId();
        AiSkillDefinition existing = mapper.selectById(id);
        if (existing == null || !existing.getProjectId().equals(projectId)) {
            return SaResult.error("Skill 不存在或无权访问");
        }
        mapper.deleteById(id);
        return SaResult.ok("已删除");
    }

    @PostMapping("/{id}/toggle")
    public SaResult toggle(@PathVariable Long id) {
        Integer projectId = ProjectContextHolder.getProjectId();
        AiSkillDefinition existing = mapper.selectById(id);
        if (existing == null || !existing.getProjectId().equals(projectId)) {
            return SaResult.error("Skill 不存在或无权访问");
        }
        existing.setEnabled(existing.getEnabled() == 1 ? 0 : 1);
        existing.setUpdateTime(new Date());
        mapper.updateById(existing);
        return SaResult.ok(existing.getEnabled() == 1 ? "已启用" : "已禁用");
    }

    /** 校验失败返回 SaResult，通过返回 null */
    private SaResult validateContent(String name, String description, String systemPrompt, boolean promptRequired) {
        if (name == null || name.isBlank()) {
            return SaResult.error("名称必填");
        }
        if (name.length() > MAX_NAME_LEN) {
            return SaResult.error("名称长度不能超过 " + MAX_NAME_LEN + " 字");
        }
        if (description != null && description.length() > MAX_DESC_LEN) {
            return SaResult.error("描述长度不能超过 " + MAX_DESC_LEN + " 字");
        }
        if (promptRequired && (systemPrompt == null || systemPrompt.isBlank())) {
            return SaResult.error("System Prompt 必填");
        }
        if (systemPrompt != null) {
            if (systemPrompt.length() > MAX_PROMPT_LEN) {
                return SaResult.error("System Prompt 长度不能超过 " + MAX_PROMPT_LEN + " 字");
            }
            if (PromptInjectionGuard.containsInjection(systemPrompt)) {
                log.warn("Skill 内容命中注入黑名单: name={}", name);
                return SaResult.error("System Prompt 包含不允许的指令内容");
            }
        }
        return null;
    }
}
