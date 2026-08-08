package com.mokatest.platform.demos.ai.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mokatest.platform.demos.ai.domain.AiConfig;
import com.mokatest.platform.demos.ai.gateway.AiGatewayService;
import com.mokatest.platform.demos.ai.gateway.ChatResult;
import com.mokatest.platform.demos.ai.mapper.AiConfigMapper;
import com.mokatest.platform.demos.ai.service.AiConfigService;
import com.mokatest.platform.demos.ai.util.AesCryptoUtil;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * AI 配置服务实现（系统级，多档案唯一生效）
 *
 * ai_config 存多行；enabled=1 为全平台唯一生效配置，激活/保存启用时
 * 事务内互斥停用其他行。读路径走单行内存缓存，任何写操作直接失效缓存。
 */
@Service
public class AiConfigServiceImpl implements AiConfigService {

    @Autowired
    private AiConfigMapper aiConfigMapper;

    @Autowired
    private ProjectPermissionChecker permissionChecker;

    /**
     * 连通性测试需要调网关；网关又依赖本服务取配置，用 @Lazy 打破循环
     */
    @Autowired
    @Lazy
    private AiGatewayService aiGatewayService;

    /** 生效行内存缓存（读多写少；任何写操作置 null 失效） */
    private volatile AiConfig cachedActive;

    @Override
    public SaResult listConfigs() {
        checkSuperAdmin();
        List<AiConfig> all = aiConfigMapper.selectList(new QueryWrapper<AiConfig>()
                .orderByDesc("enabled").orderByDesc("id"));
        List<AiConfig> masked = new ArrayList<>();
        for (AiConfig config : all) {
            AiConfig item = copy(config);
            item.setApiKey(AesCryptoUtil.mask(config.getApiKey()));
            masked.add(item);
        }
        return SaResult.ok().setData(masked);
    }

    @Override
    public SaResult getMaskedConfig() {
        checkSuperAdmin();
        AiConfig config = getActive();
        if (config == null) {
            return SaResult.ok().setData(null);
        }
        AiConfig masked = copy(config);
        masked.setApiKey(AesCryptoUtil.mask(config.getApiKey()));
        return SaResult.ok().setData(masked);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult saveConfig(AiConfig input) {
        checkSuperAdmin();
        if (input.getBaseUrl() == null || input.getBaseUrl().trim().isEmpty()) {
            throw new BusinessException("AI 服务地址不能为空");
        }
        if (input.getChatModel() == null || input.getChatModel().trim().isEmpty()) {
            throw new BusinessException("对话模型名不能为空");
        }
        String loginId = StpUtil.getLoginIdAsString();
        Date now = new Date();

        AiConfig target;
        if (input.getId() == null) {
            // 新建：API Key 必填（无历史值可沿用）
            if (input.getApiKey() == null || input.getApiKey().isEmpty() || input.getApiKey().contains("****")) {
                throw new BusinessException("新建配置请填写 API Key");
            }
            target = new AiConfig();
            target.setCreateUserId(loginId);
            target.setCreateTime(now);
        } else {
            target = aiConfigMapper.selectById(input.getId());
            if (target == null) {
                throw new BusinessException("配置不存在");
            }
        }

        target.setConfigName(input.getConfigName() == null || input.getConfigName().trim().isEmpty()
                ? input.getChatModel().trim() : input.getConfigName().trim());
        target.setProvider(input.getProvider() == null ? "openai" : input.getProvider());
        target.setBaseUrl(input.getBaseUrl().trim());
        target.setChatModel(input.getChatModel().trim());
        target.setEmbeddingModel(trimToNull(input.getEmbeddingModel()));
        target.setMaxTokens(input.getMaxTokens() == null ? 4096 : input.getMaxTokens());
        target.setTemperature(input.getTemperature());
        target.setTimeoutMs(input.getTimeoutMs() == null ? 60000 : input.getTimeoutMs());
        // enabled 未传时保持原值（编辑页不再暴露启用开关，生效切换走 activate 接口）；
        // 新建行默认未启用
        if (input.getEnabled() != null) {
            target.setEnabled(input.getEnabled());
        } else if (target.getId() == null) {
            target.setEnabled(0);
        }
        target.setVisionEnabled(input.getVisionEnabled() == null ? 0 : input.getVisionEnabled());
        target.setRemark(trimToNull(input.getRemark()));
        // apiKey：打码值（含 ****）表示未修改，保留原值；否则按新值加密
        String apiKey = input.getApiKey();
        if (apiKey != null && !apiKey.contains("****") && !apiKey.isEmpty()) {
            target.setApiKey(AesCryptoUtil.encrypt(apiKey));
        }
        target.setUpdateUserId(loginId);
        target.setUpdateTime(now);

        // 显式启用即互斥：先把其他行停用，再写本行
        if (input.getEnabled() != null && input.getEnabled() == 1) {
            deactivateAll();
        }
        if (target.getId() == null) {
            aiConfigMapper.insert(target);
        } else {
            aiConfigMapper.updateById(target);
        }
        cachedActive = null;
        return SaResult.ok("保存成功").setData(target.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult activate(Long id) {
        checkSuperAdmin();
        AiConfig target = aiConfigMapper.selectById(id);
        if (target == null) {
            throw new BusinessException("配置不存在");
        }
        deactivateAll();
        AiConfig update = new AiConfig();
        update.setId(target.getId());
        update.setEnabled(1);
        aiConfigMapper.updateById(update);
        cachedActive = null;
        return SaResult.ok("已切换生效配置：" + target.getConfigName());
    }

    @Override
    public SaResult deactivate(Long id) {
        checkSuperAdmin();
        AiConfig target = aiConfigMapper.selectById(id);
        if (target == null) {
            throw new BusinessException("配置不存在");
        }
        if (target.getEnabled() == null || target.getEnabled() != 1) {
            throw new BusinessException("该配置不是生效配置");
        }
        AiConfig update = new AiConfig();
        update.setId(target.getId());
        update.setEnabled(0);
        aiConfigMapper.updateById(update);
        cachedActive = null;
        return SaResult.ok("已停用 AI 功能：" + target.getConfigName());
    }

    @Override
    public SaResult deleteConfig(Long id) {
        checkSuperAdmin();
        AiConfig target = aiConfigMapper.selectById(id);
        if (target == null) {
            throw new BusinessException("配置不存在");
        }
        if (target.getEnabled() != null && target.getEnabled() == 1) {
            throw new BusinessException("生效中的配置不能删除，请先切换到其他配置");
        }
        aiConfigMapper.deleteById(id);
        cachedActive = null;
        return SaResult.ok("已删除");
    }

    @Override
    public SaResult testConnection(AiConfig input) {
        checkSuperAdmin();
        AiConfig testConfig = input;
        // 打码值/留空表示用该行（或生效行）已存 key 测
        if (input.getApiKey() == null || input.getApiKey().contains("****") || input.getApiKey().isEmpty()) {
            AiConfig existing = input.getId() != null
                    ? aiConfigMapper.selectById(input.getId()) : getActive();
            if (existing == null || existing.getApiKey() == null || existing.getApiKey().isEmpty()) {
                throw new BusinessException("尚未保存 API Key，请先填写后再测试");
            }
            testConfig = copy(input);
            testConfig.setApiKey(existing.getApiKey());
        } else {
            testConfig = copy(input);
            testConfig.setApiKey(AesCryptoUtil.encrypt(input.getApiKey()));
        }
        ChatResult result = aiGatewayService.testConnection(testConfig);
        return SaResult.ok("连接成功（耗时 " + result.getDurationMs() + "ms）");
    }

    @Override
    public AiConfig getActiveConfig() {
        AiConfig config = getActive();
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
            return null;
        }
        return config;
    }

    @Override
    public boolean isEnabled() {
        return getActiveConfig() != null;
    }

    @Override
    public boolean isVisionEnabled() {
        AiConfig config = getActiveConfig();
        return config != null && config.getVisionEnabled() != null && config.getVisionEnabled() == 1;
    }

    // ==================== 内部 ====================

    /** 生效行（enabled=1）单行缓存 */
    private AiConfig getActive() {
        if (cachedActive != null) {
            return cachedActive;
        }
        List<AiConfig> list = aiConfigMapper.selectList(
                new QueryWrapper<AiConfig>().eq("enabled", 1).orderByDesc("id").last("limit 1"));
        cachedActive = list == null || list.isEmpty() ? null : list.get(0);
        return cachedActive;
    }

    /** 全部停用（激活/启用互斥用） */
    private void deactivateAll() {
        aiConfigMapper.update(null, new UpdateWrapper<AiConfig>()
                .eq("enabled", 1)
                .set("enabled", 0));
    }

    private void checkSuperAdmin() {
        if (!permissionChecker.isSuperAdmin(StpUtil.getLoginIdAsString())) {
            throw new BusinessException("仅平台超级管理员可管理 AI 配置");
        }
    }

    private AiConfig copy(AiConfig source) {
        AiConfig target = new AiConfig();
        target.setId(source.getId());
        target.setConfigName(source.getConfigName());
        target.setProvider(source.getProvider());
        target.setBaseUrl(source.getBaseUrl());
        target.setApiKey(source.getApiKey());
        target.setChatModel(source.getChatModel());
        target.setEmbeddingModel(source.getEmbeddingModel());
        target.setMaxTokens(source.getMaxTokens());
        target.setTemperature(source.getTemperature());
        target.setTimeoutMs(source.getTimeoutMs());
        target.setEnabled(source.getEnabled());
        target.setVisionEnabled(source.getVisionEnabled());
        target.setRemark(source.getRemark());
        return target;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
