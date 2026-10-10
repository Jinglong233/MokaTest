package com.mokatest.platform.demos.qa.ai.agent;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 工具注册表：Spring 自动收集所有 AgentTool bean
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    public AgentToolRegistry(List<AgentTool> toolList) {
        for (AgentTool tool : toolList) {
            tools.put(tool.name(), tool);
        }
    }

    public AgentTool get(String name) {
        return tools.get(name);
    }

    /** 全部已注册工具（供 AgentController 适配为 ToolSpecification） */
    public Collection<AgentTool> all() {
        return tools.values();
    }
}
