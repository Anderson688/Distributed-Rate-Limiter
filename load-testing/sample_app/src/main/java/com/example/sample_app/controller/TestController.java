package com.example.sample_app.controller;

import com.example.distributed_rate_limiter.annotation.RateLimit;
import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class TestController {

    /**
     * SCENARIO 1: Global Limit (Hot Key)
     * All requests across all users hit the exact same Redis key ("global_limit").
     * This tests the single-threaded bottleneck of a single Redis node.
     */
    @GetMapping("/global-limited")
    @RateLimit(
            keyExpression = "'global_limit'",
            limit = 10000,
            window = 1,
            timeUnit = TimeUnit.MINUTES,
            strategy = RateLimitAlgorithm.TOKEN_BUCKET
    )
    public String globalLimit() {
        return "Global limit check passed";
    }

    /**
     * SCENARIO 2: Unique Keys (Distributed)
     * Each user hits a unique key based on their 'X-User-ID' header.
     * This tests how the library scales when traffic is distributed across Redis Hash Slots.
     */
    @GetMapping("/user-limited")
    @RateLimit(
            keyExpression = "#request.getHeader('X-User-ID')",
            limit = 5,
            window = 1,
            timeUnit = TimeUnit.MINUTES,
            strategy = RateLimitAlgorithm.SLIDING_WINDOW
    )
    public String userLimit() {
        return "User-specific limit check passed";
    }
}