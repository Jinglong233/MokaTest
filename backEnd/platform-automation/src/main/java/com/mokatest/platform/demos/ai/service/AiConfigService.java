package com.mokatest.platform.demos.ai.service;

import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.ai.domain.AiConfig;

/**
 * AI 模型接入配置服务（系统级，超管维护）
 *
 * 多档案模式：ai_config 可存多行配置，enabled=1 的行为全平台唯一生效配置
 * （激活/保存启用时事务内互斥，服务层保证）。
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
     */
    AiConfig getActiveConfig();

    /**
     * AI 功能是否已启用
     */
    boolean isEnabled();

    /**
     * 多模态（图片理解）是否已启用
     */
    boolean isVisionEnabled();
}
