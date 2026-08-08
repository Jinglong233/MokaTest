package com.mokatest.platform.demos.qa.message.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mokatest.platform.demos.qa.config.service.ProjectConfigService;
import com.mokatest.platform.demos.qa.message.domain.Message;
import com.mokatest.platform.demos.qa.message.dto.MessageContext;
import com.mokatest.platform.demos.qa.message.enums.MessageEventType;
import com.mokatest.platform.demos.qa.message.enums.NotifyScenario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知派发服务（发送决策链统一收口，业务代码零改动）：
 *
 * 事件发生
 *   → 项目规则匹配（project_config 差量，无记录按默认）：disabled → 跳过；接收角色被关 → 该角色跳过
 *   → 模板渲染（项目差量模板 → 代码内置默认模板；按场景变量白名单字面替换，未识别变量置空）
 *   → 返回待落库的消息实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotifyDispatchService {

    private final ProjectConfigService projectConfigService;

    public static final String CONFIG_TYPE_NOTIFY_RULE = "NOTIFY_RULE";

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * 状态/严重程度枚举 → 中文映射（用于 oldStatus/newStatus/severity 变量渲染）
     */
    private static final Map<String, String> VALUE_ZH = new HashMap<>();

    static {
        // BUG / 需求 / 用例状态（同名枚举中文一致）
        VALUE_ZH.put("NEW", "新建");
        VALUE_ZH.put("CONFIRMED", "已确认");
        VALUE_ZH.put("FIXING", "修复中");
        VALUE_ZH.put("FIXED", "已修复");
        VALUE_ZH.put("VERIFIED", "已验证");
        VALUE_ZH.put("CLOSED", "已关闭");
        VALUE_ZH.put("REJECTED", "已驳回");
        VALUE_ZH.put("DRAFT", "草稿");
        VALUE_ZH.put("REVIEWING", "评审中");
        VALUE_ZH.put("REVIEWED", "已评审");
        VALUE_ZH.put("DEVELOPING", "开发中");
        VALUE_ZH.put("TESTING", "测试中");
        VALUE_ZH.put("RELEASED", "已上线");
        VALUE_ZH.put("DEPRECATED", "已废弃");
        // BUG 严重程度
        VALUE_ZH.put("FATAL", "致命");
        VALUE_ZH.put("SERIOUS", "严重");
        VALUE_ZH.put("NORMAL", "一般");
        VALUE_ZH.put("TIPS", "提示");
        // BUG 优先级（需求优先级 P0~P3 不在此表，原样透传）
        VALUE_ZH.put("URGENT", "紧急");
        VALUE_ZH.put("HIGH", "高");
        VALUE_ZH.put("MEDIUM", "中");
        VALUE_ZH.put("LOW", "低");
        // BUG 环境
        VALUE_ZH.put("TEST", "测试");
        VALUE_ZH.put("STAGING", "预发");
        VALUE_ZH.put("PROD", "生产");
    }

    public static final String CONFIG_TYPE_FIELD_VISIBLE = "FIELD_VISIBLE";

    /**
     * 消息变量 → 字段显隐配置中的字段 key。
     * 项目把某字段配置为隐藏后，对应消息变量发送时同样置空（显隐语义在通知上保持一致）。
     */
    private static final Map<String, String> VAR_FIELD_MAP = new HashMap<>();

    /**
     * 业务对象 → FIELD_VISIBLE config_key
     */
    private static final Map<String, String> BIZ_CONFIG_KEY = new HashMap<>();

    static {
        VAR_FIELD_MAP.put("moduleName", "moduleId");
        VAR_FIELD_MAP.put("priority", "priority");
        VAR_FIELD_MAP.put("severity", "severity");
        VAR_FIELD_MAP.put("environment", "environment");
        VAR_FIELD_MAP.put("deadline", "deadline");
        VAR_FIELD_MAP.put("foundVersion", "foundVersion");
        VAR_FIELD_MAP.put("fixedVersion", "fixedVersion");

        BIZ_CONFIG_KEY.put("bug", "bug");
        BIZ_CONFIG_KEY.put("requirement", "requirement");
        BIZ_CONFIG_KEY.put("testCase", "test_case");
    }

    /**
     * 场景是否启用（供业务侧判断触发分支，如「创建时发 CREATED 还是 ASSIGNED」）
     */
    public boolean isScenarioEnabled(Integer projectId, MessageEventType eventType) {
        JSONObject rule = loadRule(projectId, eventType);
        return rule == null || rule.getBool("enabled", true);
    }

    /**
     * 某场景下指定接收角色是否启用（供触发点决定是否需要通知该角色）。
     * 规则未显式配置时按场景默认角色；场景本身被禁用时一律 false。
     */
    public boolean isRoleEnabled(Integer projectId, MessageEventType eventType, String role) {
        if (role == null) {
            return false;
        }
        NotifyScenario scenario = NotifyScenario.of(eventType);
        if (scenario == null) {
            return false;
        }
        JSONObject rule = loadRule(projectId, eventType);
        if (rule != null && !rule.getBool("enabled", true)) {
            return false;
        }
        boolean roleEnabled = scenario.getDefaultReceivers().contains(role);
        if (rule != null) {
            JSONObject receivers = rule.getJSONObject("receivers");
            if (receivers != null && receivers.containsKey(role)) {
                roleEnabled = receivers.getBool(role, roleEnabled);
            }
        }
        return roleEnabled;
    }

    /**
     * 按决策链构建待发送消息（跳过的不返回）
     */
    public List<Message> buildMessages(List<MessageContext> contexts) {
        List<Message> messages = new ArrayList<>();
        if (contexts == null) {
            return messages;
        }
        for (MessageContext context : contexts) {
            Message message = buildMessage(context);
            if (message != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    /**
     * 单个上下文决策 + 渲染，返回 null 表示跳过
     */
    public Message buildMessage(MessageContext context) {
        if (context == null) {
            return null;
        }
        NotifyScenario scenario = NotifyScenario.of(context.getEventType());
        if (scenario == null) {
            log.warn("未注册的通知场景，eventType={}，跳过发送", context.getEventType());
            return null;
        }
        // 不发通知给自己
        if (context.getSenderId() != null && context.getSenderId().equals(context.getReceiverId())) {
            return null;
        }
        JSONObject rule = loadRule(context.getProjectId(), context.getEventType());
        // 1. 场景开关
        if (rule != null && !rule.getBool("enabled", true)) {
            return null;
        }
        // 2. 接收角色开关（规则里显式配置优先，否则按场景默认角色）
        String role = context.getReceiverRole();
        if (role != null) {
            boolean roleEnabled = scenario.getDefaultReceivers().contains(role);
            if (rule != null) {
                JSONObject receivers = rule.getJSONObject("receivers");
                if (receivers != null && receivers.containsKey(role)) {
                    roleEnabled = receivers.getBool(role, roleEnabled);
                }
            }
            if (!roleEnabled) {
                return null;
            }
        }
        // 3. 模板：项目差量优先，缺省用代码内置默认
        String titleTemplate = scenario.getDefaultTitle();
        String contentTemplate = scenario.getDefaultContent();
        if (rule != null) {
            String customTitle = rule.getStr("titleTemplate");
            String customContent = rule.getStr("contentTemplate");
            if (customTitle != null && !customTitle.isEmpty()) {
                titleTemplate = customTitle;
            }
            if (customContent != null && !customContent.isEmpty()) {
                contentTemplate = customContent;
            }
        }
        // 4. 渲染（白名单字面替换；字段显隐中被隐藏的字段对应变量置空）
        Set<String> hiddenFields = loadHiddenFields(context.getProjectId(), scenario.getBizType());
        Message message = new Message();
        message.setReceiverId(context.getReceiverId());
        message.setSenderId(context.getSenderId());
        message.setEventType(context.getEventType());
        message.setTemplateCode(context.getEventType().name());
        message.setTitle(render(titleTemplate, context.getParams(), scenario, hiddenFields));
        message.setContent(render(contentTemplate, context.getParams(), scenario, hiddenFields));
        message.setBizType(context.getBizType());
        message.setBizId(context.getBizId());
        message.setTeamId(context.getTeamId());
        message.setProjectId(context.getProjectId());
        if (context.getSnapshot() != null && !context.getSnapshot().isEmpty()) {
            message.setExtraData(JSONUtil.toJsonStr(context.getSnapshot()));
        }
        message.setIsRead(0);
        message.setCreateTime(new Date());
        return message;
    }

    private JSONObject loadRule(Integer projectId, MessageEventType eventType) {
        if (projectId == null || eventType == null) {
            return null;
        }
        String json = projectConfigService.getConfigValue(projectId, CONFIG_TYPE_NOTIFY_RULE, eventType.name());
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            log.warn("通知规则解析失败，按默认处理，projectId={}，eventType={}", projectId, eventType, e);
            return null;
        }
    }

    /**
     * 读取项目某业务对象被隐藏的字段集合（FIELD_VISIBLE 差量，无记录 = 无隐藏）
     */
    private Set<String> loadHiddenFields(Integer projectId, String bizType) {
        Set<String> hidden = new HashSet<>();
        if (projectId == null || bizType == null) {
            return hidden;
        }
        String configKey = BIZ_CONFIG_KEY.get(bizType);
        String json = projectConfigService.getConfigValue(projectId, CONFIG_TYPE_FIELD_VISIBLE, configKey);
        if (json == null || json.isEmpty()) {
            return hidden;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            Object arr = obj.get("hiddenFields");
            if (arr instanceof Iterable) {
                for (Object item : (Iterable<?>) arr) {
                    if (item != null) {
                        hidden.add(item.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("字段显隐配置解析失败，按无隐藏处理，projectId={}，bizType={}", projectId, bizType, e);
        }
        return hidden;
    }

    /**
     * 模板渲染：${key} 字面替换，仅替换场景白名单内的 key，未识别变量置空。
     * oldStatus/newStatus/severity/priority 等枚举值渲染为中文；
     * 命中字段显隐隐藏名单的变量（如 moduleName←moduleId）置空。
     */
    private String render(String template, Map<String, Object> params, NotifyScenario scenario, Set<String> hiddenFields) {
        if (template == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = "";
            String mappedField = VAR_FIELD_MAP.get(key);
            boolean fieldHidden = mappedField != null && hiddenFields != null && hiddenFields.contains(mappedField);
            if (!fieldHidden && scenario.getVariables().contains(key) && params != null) {
                Object value = params.get(key);
                if (value != null) {
                    String text = value.toString();
                    // 仅对状态/严重程度/优先级/环境类变量做中文映射，避免标题等自由文本被误替换
                    if ("oldStatus".equals(key) || "newStatus".equals(key) || "severity".equals(key)
                            || "priority".equals(key) || "environment".equals(key)) {
                        text = VALUE_ZH.getOrDefault(text, text);
                    }
                    replacement = text;
                }
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
