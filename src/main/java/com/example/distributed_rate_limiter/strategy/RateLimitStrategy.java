package com.example.distributed_rate_limiter.strategy;

import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;

import java.util.concurrent.TimeUnit;

public interface RateLimitStrategy {
    /**
     * @param key The unique identifier for the request (e.g., user_123)
     * @param limit The maximum number of requests allowed
     * @param window The time window duration
     * @param unit The time unit of the window
     * @param algorithm The rate limit algorithm to use
     * @return A RateLimitResponse containing allow/deny status and precise retry timing
     */
    RateLimitResponse check(String key, long limit, long window, TimeUnit unit, RateLimitAlgorithm algorithm);
}