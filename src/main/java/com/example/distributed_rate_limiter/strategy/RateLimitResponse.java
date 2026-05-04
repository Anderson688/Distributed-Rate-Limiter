package com.example.distributed_rate_limiter.strategy;

public class RateLimitResponse {
    private final boolean allowed;
    private final long retryAfterSeconds;
    private final long currentUsage;

    public RateLimitResponse(boolean allowed, long retryAfterSeconds, long currentUsage) {
        this.allowed = allowed;
        this.retryAfterSeconds = retryAfterSeconds;
        this.currentUsage = currentUsage;
    }

    public boolean isAllowed() { return allowed; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
    public long getCurrentUsage() { return currentUsage; }
}