package com.mokatest.platform.demos.qa.config.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.qa.config.domain.ProjectConfig;
import com.mokatest.platform.demos.qa.config.mapper.ProjectConfigMapper;
import com.mokatest.platform.demos.qa.config.service.ProjectConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 项目级统一配置 Service 实现。
 * 读路径带内存缓存（项目配置数据量极小），写路径失效对应项目的缓存。
 */
@Slf4j
@Service
public class ProjectConfigServiceImpl extends ServiceImpl<ProjectConfigMapper, ProjectConfig> implements ProjectConfigService {

    /**
     * 缓存：projectId:configType → (configKey → configValue)
     */
    private final Map<String, Map<String, String>> cache = new ConcurrentHashMap<>();

    @Override
    public SaResult listByProject(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        QueryWrapper<ProjectConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        return SaResult.ok().setData(baseMapper.selectList(wrapper));
    }

    @Override
    @Transactional
    public SaResult saveAll(Integer projectId, List<ProjectConfig> configs) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        Integer loginId = StpUtil.getLoginIdAsInt();
        Date now = new Date();

        // 按提交中出现的 config_type 分组，逐个类型整体替换
        Set<String> types = new HashSet<>();
        if (configs != null) {
            for (ProjectConfig c : configs) {
                if (c != null && c.getConfigType() != null && c.getConfigKey() != null) {
                    types.add(c.getConfigType());
                }
            }
        }
        for (String type : types) {
            QueryWrapper<ProjectConfig> delWrapper = new QueryWrapper<>();
            delWrapper.eq("project_id", projectId).eq("config_type", type);
            baseMapper.delete(delWrapper);
        }
        if (configs != null) {
            for (ProjectConfig c : configs) {
                if (c == null || c.getConfigType() == null || c.getConfigKey() == null) {
                    continue;
                }
                ProjectConfig entity = new ProjectConfig();
                entity.setProjectId(projectId);
                entity.setConfigType(c.getConfigType());
                entity.setConfigKey(c.getConfigKey());
                entity.setConfigValue(c.getConfigValue());
                entity.setCreateUserId(loginId);
                entity.setUpdateUserId(loginId);
                entity.setCreateTime(now);
                entity.setUpdateTime(now);
                baseMapper.insert(entity);
            }
        }
        invalidateCache(projectId);
        log.info("项目配置保存成功，projectId={}，类型数={}，记录数={}",
                projectId, types.size(), configs != null ? configs.size() : 0);
        return SaResult.ok("保存成功");
    }

    @Override
    @Transactional
    public SaResult resetByProject(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        QueryWrapper<ProjectConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("project_id", projectId);
        baseMapper.delete(wrapper);
        invalidateCache(projectId);
        log.info("项目配置已重置为默认，projectId={}", projectId);
        return SaResult.ok("已恢复默认配置");
    }

    @Override
    public String getConfigValue(Integer projectId, String configType, String configKey) {
        if (projectId == null || configType == null || configKey == null) {
            return null;
        }
        return getConfigMap(projectId, configType).get(configKey);
    }

    @Override
    public Map<String, String> getConfigMap(Integer projectId, String configType) {
        if (projectId == null || configType == null) {
            return new HashMap<>();
        }
        String cacheKey = projectId + ":" + configType;
        return cache.computeIfAbsent(cacheKey, k -> {
            QueryWrapper<ProjectConfig> wrapper = new QueryWrapper<>();
            wrapper.eq("project_id", projectId).eq("config_type", configType);
            Map<String, String> map = new HashMap<>();
            for (ProjectConfig c : baseMapper.selectList(wrapper)) {
                map.put(c.getConfigKey(), c.getConfigValue());
            }
            return map;
        });
    }

    private void invalidateCache(Integer projectId) {
        String prefix = projectId + ":";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
