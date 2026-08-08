package com.mokatest.platform.demos.ai.skill;

import com.mokatest.platform.demos.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 注册表：收集所有 AiSkill / ContextSource Bean，按 scene/code 索引
 */
@Component
public class SkillRegistry {

    private final Map<String, AiSkill<?>> skills = new ConcurrentHashMap<>();
    private final Map<String, ContextSource> sources = new ConcurrentHashMap<>();

    public SkillRegistry(List<AiSkill<?>> skillList, List<ContextSource> sourceList) {
        if (skillList != null) {
            for (AiSkill<?> skill : skillList) {
                skills.put(skill.scene(), skill);
            }
        }
        if (sourceList != null) {
            for (ContextSource source : sourceList) {
                sources.put(source.code(), source);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> AiSkill<T> getSkill(String scene) {
        AiSkill<?> skill = skills.get(scene);
        if (skill == null) {
            throw new BusinessException("未注册的 AI 场景：" + scene);
        }
        return (AiSkill<T>) skill;
    }

    public ContextSource getSource(String code) {
        return sources.get(code);
    }

    public boolean hasSkill(String scene) {
        return skills.containsKey(scene);
    }
}
