package com.mokatest.platform.demos.qa.ai.agent;

import lombok.Data;

import java.util.List;

/**
 * Agent 工具执行上下文（服务端构建，工具实现只允许用此上下文的值）
 * permissions 在请求线程从 StpUtil 捕获，异步线程禁止读 StpUtil
 */
@Data
public class AgentContext {
    private Integer projectId;
    private Integer teamId;
    private String userId;
    private List<String> permissions;

    /** 检查是否拥有某权限（支持通配符 *） */
    public boolean hasPermission(String code) {
        if (permissions == null) return false;
        for (String p : permissions) {
            if (p.equals(code) || p.equals("*")) return true;
            // 通配符匹配：qa:* 匹配 qa:testcase:list
            if (p.endsWith(":*") && code.startsWith(p.substring(0, p.length() - 1))) return true;
        }
        return false;
    }
}
