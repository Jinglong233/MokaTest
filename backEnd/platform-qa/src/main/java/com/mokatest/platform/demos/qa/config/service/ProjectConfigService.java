package com.mokatest.platform.demos.qa.config.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.config.domain.ProjectConfig;

import java.util.List;
import java.util.Map;

/**
 * 项目级统一配置 Service
 */
public interface ProjectConfigService extends IService<ProjectConfig> {

    /**
     * 查询项目全部配置（差量记录）
     */
    SaResult listByProject(Integer projectId);

    /**
     * 全量保存项目配置差量：按提交中出现的 config_type 分别替换该类型下的全部记录。
     * 提交里没有的 key 视为「恢复默认」，对应记录被删除。
     */
    SaResult saveAll(Integer projectId, List<ProjectConfig> configs);

    /**
     * 重置项目全部配置（删除该项目所有差量记录，恢复平台默认）
     */
    SaResult resetByProject(Integer projectId);

    /**
     * 内部消费：读取单个配置值（带内存缓存），无记录返回 null（= 默认行为）
     */
    String getConfigValue(Integer projectId, String configType, String configKey);

    /**
     * 内部消费：读取某类型下全部配置（带内存缓存），key → configValue
     */
    Map<String, String> getConfigMap(Integer projectId, String configType);
}
