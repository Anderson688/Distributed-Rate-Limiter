package com.example.distributed_rate_limiter.strategy.inmemory;

import com.example.distributed_rate_limiter.strategy.RateLimitResponse;

import java.util.concurrent.TimeUnit;

public class FixedWindowProcessor implements InMemoryAlgorithmProcessor {

    private long count = 0;
    private long windowStartTime = System.currentTimeMillis();

    @Override
    public synchronized RateLimitResponse process(long limit, long window, TimeUnit unit) {
        long now = System.currentTimeMillis();
        long windowMillis = unit.toMillis(window);

        // 1. Check if the current time has moved past the active window
        if (now >= windowStartTime + windowMillis) {
            // Reset for a new window
            windowStartTime = now;
            count = 0;
        }

        // 2. Evaluate the limit
        if (count < limit) {
            count++;
            return new RateLimitResponse(true, 0, count);
        }

        // 3. Calculate exact wait time until the window resets
        long nextWindowStart = windowStartTime + windowMillis;
        long retryAfterMs = nextWindowStart - now;

        // Use Math.ceil to ensure we don't under-suggest wait time (e.g., 0.1s -> 1s)
        long retryAfterSeconds = (long) Math.ceil(retryAfterMs / 1000.0);

        return new RateLimitResponse(false, retryAfterSeconds, count);
    }
}