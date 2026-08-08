package com.mokatest.platform.demos.qa.message.enums;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 通知场景元数据（代码预定义、写死清单）。
 * 项目只能开关/配角色/改模板（差量存入 project_config），不能增删场景。
 *
 * 模板语法：${key} 字面替换，仅替换 variables 白名单内的 key，未识别变量置空。
 */
public enum NotifyScenario {

    BUG_CREATED(MessageEventType.BUG_CREATED, "bug", "BUG 创建",
            roles("ASSIGNEE"),
            vars("bugCode", "bugTitle", "operatorName", "severity", "priority", "projectName", "moduleName",
                    "environment", "deadline", "foundVersion"),
            "新BUG待处理",
            "BUG ${bugCode}「${bugTitle}」已由 ${operatorName} 创建并指派给您，严重程度：${severity}"),

    BUG_ASSIGNED(MessageEventType.BUG_ASSIGNED, "bug", "BUG 指派",
            roles("ASSIGNEE"),
            vars("bugCode", "bugTitle", "operatorName", "severity", "priority", "projectName", "moduleName",
                    "environment", "deadline", "foundVersion"),
            "BUG指派通知",
            "BUG ${bugCode}「${bugTitle}」已被 ${operatorName} 指派给您"),

    BUG_STATUS_CHANGED(MessageEventType.BUG_STATUS_CHANGED, "bug", "BUG 状态变更",
            roles("REPORTER", "ASSIGNEE"),
            vars("bugCode", "bugTitle", "operatorName", "severity", "oldStatus", "newStatus", "projectName", "moduleName",
                    "environment", "deadline", "foundVersion", "fixedVersion"),
            "BUG状态变更",
            "BUG ${bugCode}「${bugTitle}」状态由 ${oldStatus} 变更为 ${newStatus}"),

    BUG_UPDATED(MessageEventType.BUG_UPDATED, "bug", "BUG 更新",
            roles("REPORTER", "ASSIGNEE"),
            vars("bugCode", "bugTitle", "operatorName", "severity", "projectName", "moduleName",
                    "environment", "deadline", "foundVersion"),
            "BUG更新通知",
            "BUG ${bugCode}「${bugTitle}」的信息已被 ${operatorName} 更新"),

    BUG_DELETED(MessageEventType.BUG_DELETED, "bug", "BUG 删除",
            roles("REPORTER", "ASSIGNEE"),
            vars("bugCode", "bugTitle", "operatorName", "severity", "projectName", "moduleName",
                    "environment", "deadline", "foundVersion", "operateTime"),
            "BUG删除通知",
            "BUG ${bugCode}「${bugTitle}」已被 ${operatorName} 删除"),

    BUG_COMMENT_MENTION(MessageEventType.BUG_COMMENT_MENTION, "bug", "评论 @ 我",
            roles("MENTION"),
            vars("bugCode", "bugTitle", "operatorName", "commentContent", "severity", "projectName", "moduleName",
                    "environment", "deadline", "foundVersion"),
            "BUG评论提及",
            "${operatorName} 在 BUG ${bugCode}「${bugTitle}」的评论中@了您"),

    REQ_CREATED(MessageEventType.REQ_CREATED, "requirement", "需求创建",
            roles("OWNER"),
            vars("reqCode", "reqTitle", "operatorName", "priority", "projectName", "moduleName"),
            "新需求待处理",
            "需求 ${reqCode}「${reqTitle}」已由 ${operatorName} 创建，优先级：${priority}，您是负责人"),

    REQ_ASSIGNED(MessageEventType.REQ_ASSIGNED, "requirement", "需求指派",
            roles("OWNER"),
            vars("reqCode", "reqTitle", "operatorName", "priority", "projectName", "moduleName"),
            "需求指派通知",
            "需求 ${reqCode}「${reqTitle}」已被 ${operatorName} 指派给您"),

    REQ_STATUS_CHANGED(MessageEventType.REQ_STATUS_CHANGED, "requirement", "需求状态变更",
            roles("OWNER", "PARTICIPANT", "CREATOR"),
            vars("reqCode", "reqTitle", "operatorName", "priority", "oldStatus", "newStatus", "projectName", "moduleName"),
            "需求状态变更",
            "需求 ${reqCode}「${reqTitle}」状态由 ${oldStatus} 变更为 ${newStatus}"),

    REQ_UPDATED(MessageEventType.REQ_UPDATED, "requirement", "需求更新",
            roles("OWNER", "PARTICIPANT"),
            vars("reqCode", "reqTitle", "operatorName", "priority", "projectName", "moduleName"),
            "需求更新通知",
            "需求 ${reqCode}「${reqTitle}」的信息已被 ${operatorName} 更新"),

    REQ_DELETED(MessageEventType.REQ_DELETED, "requirement", "需求删除",
            roles("OWNER", "PARTICIPANT"),
            vars("reqCode", "reqTitle", "operatorName", "projectName", "moduleName", "operateTime"),
            "需求删除通知",
            "需求 ${reqCode}「${reqTitle}」已被 ${operatorName} 删除"),

    CASE_CREATED(MessageEventType.CASE_CREATED, "testCase", "用例创建",
            roles("CREATOR"),
            vars("caseCode", "caseName", "operatorName", "projectName", "moduleName"),
            "用例创建通知",
            "用例 ${caseCode}「${caseName}」已由 ${operatorName} 创建"),

    CASE_STATUS_CHANGED(MessageEventType.CASE_STATUS_CHANGED, "testCase", "用例状态变更",
            roles("CREATOR"),
            vars("caseCode", "caseName", "operatorName", "oldStatus", "newStatus", "projectName", "moduleName"),
            "用例状态变更",
            "用例 ${caseCode}「${caseName}」状态由 ${oldStatus} 变更为 ${newStatus}");

    private final MessageEventType eventType;
    private final String bizType;
    private final String scenarioName;
    /**
     * 默认开启的接收角色
     */
    private final Set<String> defaultReceivers;
    /**
     * 可用模板变量白名单
     */
    private final Set<String> variables;
    private final String defaultTitle;
    private final String defaultContent;

    NotifyScenario(MessageEventType eventType, String bizType, String scenarioName,
                   Set<String> defaultReceivers, Set<String> variables,
                   String defaultTitle, String defaultContent) {
        this.eventType = eventType;
        this.bizType = bizType;
        this.scenarioName = scenarioName;
        this.defaultReceivers = defaultReceivers;
        this.variables = variables;
        this.defaultTitle = defaultTitle;
        this.defaultContent = defaultContent;
    }

    public MessageEventType getEventType() {
        return eventType;
    }

    public String getBizType() {
        return bizType;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public Set<String> getDefaultReceivers() {
        return defaultReceivers;
    }

    public Set<String> getVariables() {
        return variables;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }

    public String getDefaultContent() {
        return defaultContent;
    }

    public static NotifyScenario of(MessageEventType eventType) {
        if (eventType == null) {
            return null;
        }
        for (NotifyScenario s : values()) {
            if (s.eventType == eventType) {
                return s;
            }
        }
        return null;
    }

    private static Set<String> roles(String... roles) {
        return new LinkedHashSet<>(Arrays.asList(roles));
    }

    private static Set<String> vars(String... vars) {
        return new LinkedHashSet<>(Arrays.asList(vars));
    }
}
