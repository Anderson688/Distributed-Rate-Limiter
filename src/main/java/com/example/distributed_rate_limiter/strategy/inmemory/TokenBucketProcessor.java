package com.example.distributed_rate_limiter.strategy.inmemory;

import com.example.distributed_rate_limiter.strategy.RateLimitResponse;

import java.util.concurrent.TimeUnit;

public class TokenBucketProcessor implements InMemoryAlgorithmProcessor {
    private long tokens;
    private long lastRefillTime = System.currentTimeMillis();

    @Override
    public synchronized RateLimitResponse process(long limit, long window, TimeUnit unit) {
        long now = System.currentTimeMillis();
        long refillInterval = unit.toMillis(window) / limit; // Time per token

        // Refill logic
        long passedTime = now - lastRefillTime;
        long refillTokens = passedTime / refillInterval;

        if (refillTokens > 0) {
            tokens = Math.min(limit, tokens + refillTokens);
            lastRefillTime = now;
        }

        if (tokens > 0) {
            tokens--;
            return new RateLimitResponse(true, 0, tokens);
        }
        return new RateLimitResponse(false, unit.toSeconds(window), 0);
    }
}