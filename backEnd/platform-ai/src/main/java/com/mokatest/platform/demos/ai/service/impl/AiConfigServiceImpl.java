package com.mokatest.platform.demos.ai.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mokatest.platform.demos.ai.domain.AiConfig;
import com.mokatest.platform.demos.ai.config.ModelManager;
import com.mokatest.platform.demos.ai.mapper.AiConfigMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import com.mokatest.platform.demos.ai.service.AiConfigService;
import com.mokatest.platform.demos.ai.util.AesCryptoUtil;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.common.SuperAdminChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * AI 配置服务实现（平台级 + 项目级，按作用域互斥生效）
 *
 * 解析顺序：项目启用档案 → 平台启用档案 → null
 * 缓存：按 projectId 维度缓存（null key = 平台档案）
 */
@Service
public class AiConfigServiceImpl implements AiConfigService {

    @Autowired
    private AiConfigMapper aiConfigMapper;

    @Autowired
    private SuperAdminChecker permissionChecker;

    /**
     * 模型管理器需要调配置；配置变更时需失效模型缓存，用 @Lazy 打破循环
     */
    @Autowired
    @Lazy
    private ModelManager modelManager;

    /** 按项目维度缓存生效行（key=projectId，null key=平台档案） */
    private final java.util.concurrent.ConcurrentHashMap<Integer, AiConfig> cachedByProject = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public SaResult listConfigs() {
        Integer projectId = currentProjectId();
        // 纯项目级：始终按当前项目过滤（超管也看当前项目，切换项目即切换上下文）
        List<AiConfig> configs = aiConfigMapper.selectList(new QueryWrapper<AiConfig>()
                .eq("project_id", projectId).orderByDesc("enabled").orderByDesc("id"));
        return SaResult.ok().setData(maskList(configs));
    }

    @Override
    public SaResult getMaskedConfig() {
        AiConfig config = queryActiveByProject(currentProjectId());
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
        Integer projectId = currentProjectId();
        if (input.getBaseUrl() == null || input.getBaseUrl().trim().isEmpty()) {
            throw new BusinessException("AI 服务地址不能为空");
        }
        if (input.getChatModel() == null || input.getChatModel().trim().isEmpty()) {
            throw new BusinessException("对话模型名不能为空");
        }
        // 向量模型必填：无向量模型时知识库只能降级关键词检索，不再允许留空
        if (input.getEmbeddingModel() == null || input.getEmbeddingModel().trim().isEmpty()) {
            throw new BusinessException("向量模型不能为空");
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
            // 不信前端 projectId：从 ProjectContextHolder 取，超管运维时也用当前项目上下文
            target.setProjectId(projectId);
            if (projectId == null) {
                throw new BusinessException("无法确定当前项目，请切换到项目后再配置");
            }
        } else {
            target = aiConfigMapper.selectById(input.getId());
            if (target == null) {
                throw new BusinessException("配置不存在");
            }
            // 归属校验（项目管理员不能操作他人项目档案；超管可跨项目运维）
            if (!isSuperAdmin() && (target.getProjectId() == null || !target.getProjectId().equals(projectId))) {
                throw new BusinessException("无权操作此配置");
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
        target.setSupportJsonMode(input.getSupportJsonMode() == null ? 0 : input.getSupportJsonMode());
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
            deactivateAll(target.getProjectId());
        }
        if (target.getId() == null) {
            aiConfigMapper.insert(target);
        } else {
            aiConfigMapper.updateById(target);
        }
        invalidateAllCache();
        modelManager.invalidate();
        return SaResult.ok("保存成功").setData(target.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SaResult activate(Long id) {
        Integer projectId = currentProjectId();
        AiConfig target = aiConfigMapper.selectById(id);
        if (target == null) {
            throw new BusinessException("配置不存在");
        }
        // 归属校验
        if (!isSuperAdmin() && (target.getProjectId() == null || !target.getProjectId().equals(projectId))) {
            throw new BusinessException("无权操作此配置");
        }
        deactivateAll(target.getProjectId());
        AiConfig update = new AiConfig();
        update.setId(target.getId());
        update.setEnabled(1);
        aiConfigMapper.updateById(update);
        invalidateAllCache();
        modelManager.invalidate();
        return SaResult.ok("已切换生效配置：" + target.getConfigName());
    }

    @Override
    public SaResult deactivate(Long id) {
        Integer projectId = currentProjectId();
        AiConfig target = aiConfigMapper.selectById(id);
        if (target == null) {
            throw new BusinessException("配置不存在");
        }
        // 归属校验
        if (!isSuperAdmin() && (target.getProjectId() == null || !target.getProjectId().equals(projectId))) {
            throw new BusinessException("无权操作此配置");
        }
        if (target.getEnabled() == null || target.getEnabled() != 1) {
            throw new BusinessException("该配置不是生效配置");
        }
        AiConfig update = new AiConfig();
        update.setId(target.getId());
        update.setEnabled(0);
        aiConfigMapper.updateById(update);
        invalidateAllCache();
        modelManager.invalidate();
        return SaResult.ok("已停用 AI 功能：" + target.getConfigName());
    }

    @Override
    public SaResult deleteConfig(Long id) {
        Integer projectId = currentProjectId();
        AiConfig target = aiConfigMapper.selectById(id);
        if (target == null) {
            throw new BusinessException("配置不存在");
        }
        // 归属校验
        if (!isSuperAdmin() && (target.getProjectId() == null || !target.getProjectId().equals(projectId))) {
            throw new BusinessException("无权操作此配置");
        }
        if (target.getEnabled() != null && target.getEnabled() == 1) {
            throw new BusinessException("生效中的配置不能删除，请先切换到其他配置");
        }
        aiConfigMapper.deleteById(id);
        invalidateAllCache();
        modelManager.invalidate();
        return SaResult.ok("已删除");
    }

    @Override
    public SaResult testConnection(AiConfig input) {
        Integer projectId = currentProjectId();
        AiConfig testConfig = input;
        // 打码值/留空表示用该行已存 key 测
        if (input.getApiKey() == null || input.getApiKey().contains("****") || input.getApiKey().isEmpty()) {
            AiConfig existing = input.getId() != null
                    ? aiConfigMapper.selectById(input.getId()) : queryActiveByProject(projectId);
            if (existing == null || existing.getApiKey() == null || existing.getApiKey().isEmpty()) {
                throw new BusinessException("尚未保存 API Key，请先填写后再测试");
            }
            testConfig = copy(input);
            testConfig.setApiKey(existing.getApiKey());
        } else {
            testConfig = copy(input);
            testConfig.setApiKey(AesCryptoUtil.encrypt(input.getApiKey()));
        }
        long start = System.currentTimeMillis();
        ChatModel chatModel = modelManager.getChatModel(testConfig);
        ChatRequest req = ChatRequest.builder()
                .messages(UserMessage.from("ping"))
                .build();
        chatModel.chat(req);
        int duration = (int) (System.currentTimeMillis() - start);
        return SaResult.ok("连接成功（耗时 " + duration + "ms）");
    }

    @Override
    @Deprecated
    public AiConfig getActiveConfig() {
        return getActiveConfig(null);
    }

    @Override
    public AiConfig getActiveConfig(Integer projectId) {
        if (projectId == null) return null;
        Integer cacheKey = projectId;
        AiConfig cached = cachedByProject.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        // 纯项目级：只查项目启用档案，不回落平台
        AiConfig config = queryActiveByProject(projectId);
        if (config != null) {
            cachedByProject.put(cacheKey, config);
        }
        return config;
    }

    @Override
    public boolean isEnabled() {
        return getActiveConfig(currentProjectId()) != null;
    }

    @Override
    public boolean isVisionEnabled() {
        AiConfig config = getActiveConfig(currentProjectId());
        return config != null && config.getVisionEnabled() != null && config.getVisionEnabled() == 1;
    }

    // ==================== 内部 ====================

    /** 从线程上下文获取项目 ID */
    private Integer currentProjectId() {
        try {
            return com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder.getProjectId();
        } catch (Exception e) {
            return null;
        }
    }

    /** 是否超管 */
    private boolean isSuperAdmin() {
        return permissionChecker.isSuperAdmin(StpUtil.getLoginIdAsString());
    }

    /** 批量脱敏 */
    private List<AiConfig> maskList(List<AiConfig> configs) {
        List<AiConfig> masked = new ArrayList<>();
        for (AiConfig config : configs) {
            AiConfig item = copy(config);
            item.setApiKey(AesCryptoUtil.mask(config.getApiKey()));
            item.setProjectId(config.getProjectId());
            masked.add(item);
        }
        return masked;
    }

    /** 查项目启用档案（纯项目级，无平台回落） */
    private AiConfig queryActiveByProject(Integer projectId) {
        if (projectId == null) return null;
        List<AiConfig> list = aiConfigMapper.selectList(
                new QueryWrapper<AiConfig>().eq("enabled", 1).eq("project_id", projectId)
                        .orderByDesc("id").last("limit 1"));
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    /** 失效全部缓存（写操作后调用） */
    private void invalidateAllCache() {
        cachedByProject.clear();
    }

    /** 同项目全部停用（激活互斥用，纯项目级无 null 分支） */
    private void deactivateAll(Integer projectId) {
        if (projectId == null) return;
        aiConfigMapper.update(null, new UpdateWrapper<AiConfig>()
                .eq("enabled", 1).eq("project_id", projectId)
                .set("enabled", 0));
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
        target.setSupportJsonMode(source.getSupportJsonMode());
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
