package com.mokatest.platform.demos.ai.gateway;

import com.mokatest.platform.demos.ai.domain.AiConfig;
import com.mokatest.platform.demos.ai.mapper.AiUsageLogMapper;
import com.mokatest.platform.demos.ai.service.AiConfigService;
import com.mokatest.platform.demos.ai.util.AesCryptoUtil;
import com.mokatest.platform.demos.exception.BusinessException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 网关单元测试（MockWebServer 模拟 OpenAI 兼容端点）
 */
class AiGatewayServiceTest {

    private MockWebServer server;
    private AiGatewayService gateway;
    private AiUsageLogMapper usageLogMapper;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        AiConfig config = new AiConfig();
        config.setBaseUrl(server.url("/v1").toString());
        config.setApiKey(AesCryptoUtil.encrypt("sk-test"));
        config.setChatModel("test-model");
        config.setMaxTokens(100);
        config.setTemperature(new BigDecimal("0.3"));
        config.setTimeoutMs(5000);

        AiConfigService configService = Mockito.mock(AiConfigService.class);
        Mockito.when(configService.getActiveConfig()).thenReturn(config);

        usageLogMapper = Mockito.mock(AiUsageLogMapper.class);

        gateway = new AiGatewayService();
        ReflectionTestUtils.setField(gateway, "aiConfigService", configService);
        ReflectionTestUtils.setField(gateway, "usageLogMapper", usageLogMapper);
        ReflectionTestUtils.setField(gateway, "rateLimiter", new AiRateLimiter());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private ChatOptions options() {
        ChatOptions o = new ChatOptions();
        o.setScene("GENERATE_CASE");
        o.setUserId("u1");
        o.setProjectId(1);
        o.setTeamId(1);
        return o;
    }

    @Test
    void chatSuccess() throws Exception {
        server.enqueue(new MockResponse().setBody(
                "{\"choices\":[{\"message\":{\"content\":\"hello ai\"}}],\"usage\":{\"total_tokens\":42}}"));

        ChatResult result = gateway.chat(Collections.singletonList(ChatMessage.user("hi")), options());

        assertEquals("hello ai", result.getContent());
        assertEquals(42, result.getTokens());
        RecordedRequest req = server.takeRequest();
        assertEquals("/v1/chat/completions", req.getPath());
        assertEquals("Bearer sk-test", req.getHeader("Authorization"));
        // 成功也写日志
        Mockito.verify(usageLogMapper, Mockito.atLeastOnce()).insert(Mockito.any(com.mokatest.platform.demos.ai.domain.AiUsageLog.class));
    }

    @Test
    void chatHttpErrorThrowsAndLogs() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> gateway.chat(Collections.singletonList(ChatMessage.user("hi")), options()));
        assertTrue(ex.getMessage().contains("500"));
        Mockito.verify(usageLogMapper, Mockito.atLeastOnce()).insert(Mockito.any(com.mokatest.platform.demos.ai.domain.AiUsageLog.class));
    }

    @Test
    void rateLimitBlocks() {
        ChatOptions o = options();
        for (int i = 0; i < 10; i++) {
            server.enqueue(new MockResponse().setBody(
                    "{\"choices\":[{\"message\":{\"content\":\"x\"}}]}"));
            gateway.chat(Collections.singletonList(ChatMessage.user("hi")), o);
        }
        assertThrows(BusinessException.class,
                () -> gateway.chat(Collections.singletonList(ChatMessage.user("hi")), o));
    }

    @Test
    void embedSuccess() throws Exception {
        AiConfigService configService = (AiConfigService) ReflectionTestUtils.getField(gateway, "aiConfigService");
        AiConfig config = ((AiConfigService) configService).getActiveConfig();
        config.setEmbeddingModel("emb-model");
        server.enqueue(new MockResponse().setBody(
                "{\"data\":[{\"embedding\":[0.1,0.2,0.3]}],\"usage\":{\"total_tokens\":3}}"));

        ChatOptions o = options();
        o.setScene("EMBEDDING");
        float[] vector = gateway.embed("测试文本", o);
        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, vector, 0.0001f);
        assertEquals("/v1/embeddings", server.takeRequest().getPath());
    }

    @Test
    void embedWithoutModelThrows() {
        ChatOptions o = options();
        o.setScene("EMBEDDING");
        BusinessException ex = assertThrows(BusinessException.class, () -> gateway.embed("t", o));
        assertTrue(ex.getMessage().contains("未配置向量模型"));
    }
}
