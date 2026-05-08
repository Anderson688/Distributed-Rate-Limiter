package com.example.distributed_rate_limiter.strategy.inmemory;

import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;
import com.example.distributed_rate_limiter.strategy.RateLimitResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRateLimitStrategyTest {

    private InMemoryRateLimitStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new InMemoryRateLimitStrategy();
    }

    @Test
    @DisplayName("Should route to Token Bucket and isolate by key")
    void testTokenBucketRoutingAndIsolation() {
        String key1 = "rl:demo:API:user_A";
        String key2 = "rl:demo:API:user_B";

        // User A exhausts their limit (Limit 1)
        RateLimitResponse r1 = strategy.check(key1, 1, 10, TimeUnit.SECONDS, RateLimitAlgorithm.TOKEN_BUCKET);
        assertTrue(r1.isAllowed());
        RateLimitResponse r2 = strategy.check(key1, 1, 10, TimeUnit.SECONDS, RateLimitAlgorithm.TOKEN_BUCKET);
        assertFalse(r2.isAllowed()); // Denied

        // User B should still be allowed because they are isolated!
        RateLimitResponse r3 = strategy.check(key2, 1, 10, TimeUnit.SECONDS, RateLimitAlgorithm.TOKEN_BUCKET);
        assertTrue(r3.isAllowed()); // Allowed
    }

    @Test
    @DisplayName("Should default to Token Bucket if DEFAULT algorithm is passed")
    void testDefaultAlgorithmFallback() {
        // Even with DEFAULT, it should still function without throwing a NullPointerException
        RateLimitResponse response = strategy.check("test_key", 5, 1, TimeUnit.MINUTES, RateLimitAlgorithm.DEFAULT);
        assertTrue(response.isAllowed());
    }
}