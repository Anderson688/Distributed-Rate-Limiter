package com.example.distributed_rate_limiter.strategy.inmemory;

import com.example.distributed_rate_limiter.strategy.RateLimitResponse;

import java.util.concurrent.TimeUnit;

public interface InMemoryAlgorithmProcessor {
    RateLimitResponse process(long limit, long window, TimeUnit unit);
}