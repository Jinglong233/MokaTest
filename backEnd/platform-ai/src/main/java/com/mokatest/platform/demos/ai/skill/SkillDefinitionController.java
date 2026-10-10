package com.mokatest.platform.demos.ai.skill;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.domain.AiSkillDefinition;
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
 */
@Slf4j
@RestController
@RequestMapping("/ai/skill-def")
@RequiredArgsConstructor
@SaCheckPermission("qa:ai:use")
public class SkillDefinitionController {

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
        if (body.getName() == null || body.getName().isBlank()) {
            return SaResult.error("名称必填");
        }
        if (body.getSystemPrompt() == null || body.getSystemPrompt().isBlank()) {
            return SaResult.error("System Prompt 必填");
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
}
