package com.example.distributed_rate_limiter.strategy.inmemory;

import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;
import com.example.distributed_rate_limiter.strategy.RateLimitResponse;
import com.example.distributed_rate_limiter.strategy.RateLimitStrategy;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
public class InMemoryRateLimitStrategy implements RateLimitStrategy {

    // The cache stores the Processor (the state) for each unique key
    private final Cache<String, InMemoryAlgorithmProcessor> stateStore = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1)) // Cleanup inactive keys
            .maximumSize(100_000) // Prevent memory exhaustion
            .build();

    public InMemoryRateLimitStrategy() {
        log.warn("RateLimiter: Using IN_MEMORY mode. This instance will NOT sync with other nodes.");
    }

    @Override
    public RateLimitResponse check(String key, long limit, long window,
                                   TimeUnit unit, RateLimitAlgorithm algorithm) {

        // Composite key ensures UserA-TokenBucket and UserA-SlidingWindow don't conflict
        String cacheKey = algorithm.name() + ":" + key;

        // Get the existing processor or create a new one for this specific algorithm
        InMemoryAlgorithmProcessor processor = stateStore.get(cacheKey, k -> createProcessor(algorithm));

        return processor.process(limit, window, unit);
    }

    private InMemoryAlgorithmProcessor createProcessor(RateLimitAlgorithm algorithm) {
        return switch (algorithm) {
            case TOKEN_BUCKET -> new TokenBucketProcessor();
            case SLIDING_WINDOW -> new SlidingWindowProcessor();
            case FIXED_WINDOW -> new FixedWindowProcessor();
            default -> new TokenBucketProcessor();
        };
    }
}