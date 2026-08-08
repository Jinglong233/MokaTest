package com.mokatest.platform.demos.ai.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 限流器单元测试
 */
class AiRateLimiterTest {

    @Test
    void allowsTenPerMinute() {
        AiRateLimiter limiter = new AiRateLimiter();
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.acquire("u1"), "第 " + (i + 1) + " 次应放行");
        }
        assertFalse(limiter.acquire("u1"), "第 11 次应被限流");
    }

    @Test
    void separateBucketsPerUser() {
        AiRateLimiter limiter = new AiRateLimiter();
        for (int i = 0; i < 10; i++) {
            limiter.acquire("u1");
        }
        assertFalse(limiter.acquire("u1"));
        assertTrue(limiter.acquire("u2"), "不同用户互不影响");
        assertTrue(limiter.acquire(null), "匿名用户独立桶");
    }
}
