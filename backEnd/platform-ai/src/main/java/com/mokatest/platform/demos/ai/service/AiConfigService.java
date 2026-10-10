package com.mokatest.platform.demos.ai.service;

import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.ai.domain.AiConfig;

/**
 * AI 模型接入配置服务（平台级 + 项目级）
 *
 * 解析顺序：项目启用档案 → 平台启用档案 → null（未启用）
 * 同一 project_id 内 enabled=1 唯一；平台档案（project_id IS NULL）内唯一
 */
public interface AiConfigService {

    /**
     * 配置档案列表（apiKey 打码返回，按生效行置顶）
     */
    SaResult listConfigs();

    /**
     * 获取当前生效配置（apiKey 打码返回）
     */
    SaResult getMaskedConfig();

    /**
     * 保存配置（超管）：无 id 新建、有 id 按行更新；enabled=1 时互斥停用其他行
     */
    SaResult saveConfig(AiConfig config);

    /**
     * 激活指定配置为唯一生效行（超管）
     */
    SaResult activate(Long id);

    /**
     * 停用生效配置（超管）：全平台 AI 功能进入"未启用"状态，仅允许对当前生效行操作
     */
    SaResult deactivate(Long id);

    /**
     * 删除配置（超管）；生效中的配置禁止删除
     */
    SaResult deleteConfig(Long id);

    /**
     * 连通性测试：使用传入或已保存的配置实际调一次 chat 接口（超管）
     */
    SaResult testConnection(AiConfig config);

    /**
     * 获取生效配置（内部使用，apiKey 已解密）；未配置或未启用时返回 null
     * @deprecated 用 {@link #getActiveConfig(Integer)} 代替，支持项目维度解析
     */
    @Deprecated
    AiConfig getActiveConfig();

    /**
     * 获取生效配置（项目维度解析）：项目启用档案 → 平台启用档案 → null
     * @param projectId 项目 ID（null 时查平台档案）
     */
    AiConfig getActiveConfig(Integer projectId);

    /**
     * AI 功能是否已启用
     */
    boolean isEnabled();

    /**
     * 多模态（图片理解）是否已启用
     */
    boolean isVisionEnabled();
}
