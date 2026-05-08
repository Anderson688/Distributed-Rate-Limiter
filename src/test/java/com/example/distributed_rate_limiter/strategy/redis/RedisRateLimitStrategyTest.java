package com.example.distributed_rate_limiter.strategy.redis;

import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;
import com.example.distributed_rate_limiter.strategy.RateLimitResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RedisRateLimitStrategyTest {

    private StringRedisTemplate redisTemplate;
    private RedisRateLimitStrategy strategy;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        strategy = new RedisRateLimitStrategy(redisTemplate);
    }

    @Test
    @DisplayName("Should parse a successful ALLOW response from Redis Lua script")
    void testCheckAllowed() {
        List<Long> mockRedisResponse = Arrays.asList(1L, 0L, 5L);

        // FIX: Explicitly match the 3 strings passed in your Varargs
        Mockito.when(redisTemplate.execute(
                Mockito.any(RedisScript.class),
                Mockito.anyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
        )).thenReturn(mockRedisResponse);

        RateLimitResponse response = strategy.check("test_key", 10, 1, TimeUnit.MINUTES, RateLimitAlgorithm.TOKEN_BUCKET);

        assertTrue(response.isAllowed());
        assertEquals(0, response.getRetryAfterSeconds());
        assertEquals(5, response.getCurrentUsage());
    }

    @Test
    @DisplayName("Should parse a DENY response and calculate seconds correctly")
    void testCheckDenied() {
        List<Long> mockRedisResponse = Arrays.asList(0L, 1500L, 10L);

        // FIX: Explicitly match the 3 strings passed in your Varargs
        Mockito.when(redisTemplate.execute(
                Mockito.any(RedisScript.class),
                Mockito.anyList(),
                Mockito.anyString(),
                Mockito.anyString(),
                Mockito.anyString()
        )).thenReturn(mockRedisResponse);

        RateLimitResponse response = strategy.check("test_key", 10, 1, TimeUnit.MINUTES, RateLimitAlgorithm.TOKEN_BUCKET);

        assertFalse(response.isAllowed());
        assertEquals(2, response.getRetryAfterSeconds());
    }
}