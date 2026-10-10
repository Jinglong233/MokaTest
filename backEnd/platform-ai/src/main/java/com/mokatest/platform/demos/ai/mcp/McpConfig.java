package com.mokatest.platform.demos.ai.mcp;

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 配置：用 LangChain4j McpClient + StreamableHttpMcpTransport 替代旧 McpController 空壳。
 *
 * mokatest.mcp.enabled=true 时创建 McpToolProvider，供 AgentAssistant 通过 .toolProvider() 使用。
 */
@Configuration
@ConditionalOnProperty(name = "mokatest.mcp.enabled", havingValue = "true")
public class McpConfig {

    @Value("${mokatest.mcp.server-url:http://localhost:3001/mcp}")
    private String mcpServerUrl;

    private McpClient mcpClient;

    @Bean
    public ToolProvider mcpToolProvider() {
        McpTransport transport = StreamableHttpMcpTransport.builder()
                .url(mcpServerUrl)
                .build();
        mcpClient = DefaultMcpClient.builder()
                .transport(transport)
                .build();
        return McpToolProvider.builder()
                .mcpClients(mcpClient)
                .build();
    }

    @PreDestroy
    public void destroy() {
        if (mcpClient != null) {
            try {
                mcpClient.close();
            } catch (Exception ignored) {
            }
        }
    }
}
