package com.example.distributed_rate_limiter.strategy.inmemory;

import com.example.distributed_rate_limiter.strategy.RateLimitResponse;
import java.util.concurrent.TimeUnit;

public class TokenBucketProcessor implements InMemoryAlgorithmProcessor {
    private long tokens;
    private long lastRefillTime;
    private boolean initialized = false;

    @Override
    public synchronized RateLimitResponse process(long limit, long window, TimeUnit unit) {
        long now = System.currentTimeMillis();
        long refillInterval = unit.toMillis(window) / limit;

        // 1. Initialize full bucket on the very first request
        if (!initialized) {
            tokens = limit;
            lastRefillTime = now;
            initialized = true;
        }

        // 2. Refill logic
        long passedTime = now - lastRefillTime;
        long refillTokens = passedTime / refillInterval;

        if (refillTokens > 0) {
            tokens = Math.min(limit, tokens + refillTokens);
            // Move clock forward precisely to avoid fractional time loss
            lastRefillTime = lastRefillTime + (refillTokens * refillInterval);
        }

        // 3. Evaluate limits
        if (tokens > 0) {
            tokens--;
            return new RateLimitResponse(true, 0, tokens);
        }

        // 4. Precise Retry-After calculation
        long timeToNextToken = refillInterval - (now - lastRefillTime);
        long retryAfterSeconds = (long) Math.ceil(timeToNextToken / 1000.0);

        return new RateLimitResponse(false, retryAfterSeconds, 0);
    }
}