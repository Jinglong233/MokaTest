package com.mokatest.platform.demos.api.http.config;

public class RetryConfig {
    private final int maxRetries;
    private final long retryIntervalMs;
    private final java.util.Set<Integer> retryStatusCodes;

    public RetryConfig(int maxRetries, long retryIntervalMs) {
        this(maxRetries, retryIntervalMs, new java.util.HashSet<>());
    }

    public RetryConfig(int maxRetries, long retryIntervalMs, java.util.Set<Integer> retryStatusCodes) {
        this.maxRetries = maxRetries;
        this.retryIntervalMs = retryIntervalMs;
        this.retryStatusCodes = retryStatusCodes;
        if (retryStatusCodes.isEmpty()) {
            retryStatusCodes.add(500);
            retryStatusCodes.add(502);
            retryStatusCodes.add(503);
            retryStatusCodes.add(504);
        }
    }

    // getters...
    public int getMaxRetries() { return maxRetries; }
    public long getRetryIntervalMs() { return retryIntervalMs; }
    public java.util.Set<Integer> getRetryStatusCodes() { return retryStatusCodes; }
}
