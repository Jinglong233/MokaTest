package com.mokatest.platform.demos.ai.gateway;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 调用限流器：每用户每分钟 10 次（内存滑动窗口）
 *
 * 单体部署足够；多实例部署时可平滑替换为 Redis 实现（保持 acquire 签名不变）。
 */
@Component
public class AiRateLimiter {

    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_PER_WINDOW = 10;

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /**
     * 尝试获取一次调用额度
     *
     * @param userId 用户ID（为空时按匿名共享一个桶）
     * @return true 表示放行；false 表示超限
     */
    public boolean acquire(String userId) {
        String key = userId == null ? "anonymous" : userId;
        long now = System.currentTimeMillis();
        Deque<Long> deque = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > WINDOW_MS) {
                deque.pollFirst();
            }
            if (deque.size() >= MAX_PER_WINDOW) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /** 测试用：清空所有窗口 */
    public void reset() {
        windows.clear();
    }
}
