package com.example.distributed_rate_limiter.strategy.redis;

import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;
import com.example.distributed_rate_limiter.strategy.RateLimitResponse;
import com.example.distributed_rate_limiter.strategy.RateLimitStrategy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Distributed Redis-based implementation of the RateLimitStrategy.
 * Uses Lua scripting to ensure atomicity and precision.
 */
public class RedisRateLimitStrategy implements RateLimitStrategy {

    private final StringRedisTemplate redisTemplate;
    // Map to store pre-loaded scripts for each algorithm
    private final Map<RateLimitAlgorithm, RedisScript<List>> scripts = new EnumMap<>(RateLimitAlgorithm.class);

    public RedisRateLimitStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        loadScripts();
    }

    private void loadScripts() {
        scripts.put(RateLimitAlgorithm.SLIDING_WINDOW,
                createScript("scripts/redis/sliding_window.lua"));

        scripts.put(RateLimitAlgorithm.TOKEN_BUCKET,
                createScript("scripts/redis/token_bucket.lua"));

        scripts.put(RateLimitAlgorithm.FIXED_WINDOW,
                createScript("scripts/redis/fixed_window.lua"));
    }

    private RedisScript<List> createScript(String path) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(List.class);
        return script;
    }

    @Override
    public RateLimitResponse check(String key, long limit, long window,
                                   TimeUnit unit, RateLimitAlgorithm algorithm) {

        // 1. Pick the correct script based on the algorithm passed by the Aspect
        RedisScript<List> script = scripts.get(algorithm);

        if (script == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }

        long windowMillis = unit.toMillis(window);
        String uniqueRequestId = UUID.randomUUID().toString();

        try {
            // 2. Execute the selected script
            List<Long> result = redisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    String.valueOf(limit),
                    String.valueOf(windowMillis),
                    uniqueRequestId
            );

            return parseResult(result);

        } catch (Exception e) {
            // Requirement 15: If Redis is down, we let the exception bubble up
            // to the Aspect, which will handle the "Fail-Open" logic.
            throw e;
        }
    }

    private RateLimitResponse parseResult(List<Long> result) {
        if (result == null || result.size() < 3) {
            return new RateLimitResponse(true, 0, 0);
        }
        boolean isAllowed = result.get(0) == 1L;
        long retryAfterMs = result.get(1);
        long currentUsage = result.get(2);
        long retryAfterSeconds = (long) Math.ceil(retryAfterMs / 1000.0);

        return new RateLimitResponse(isAllowed, retryAfterSeconds, currentUsage);
    }
}