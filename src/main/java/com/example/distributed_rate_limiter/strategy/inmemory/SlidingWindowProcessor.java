package com.example.distributed_rate_limiter.strategy.inmemory;

import com.example.distributed_rate_limiter.strategy.RateLimitResponse;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class SlidingWindowProcessor implements InMemoryAlgorithmProcessor {
    private final Queue<Long> windowLog = new LinkedList<>();

    @Override
    public synchronized RateLimitResponse process(long limit, long window, TimeUnit unit) {
        long now = System.currentTimeMillis();
        long windowMillis = unit.toMillis(window);
        long boundary = now - windowMillis;

        // Remove timestamps outside the current window
        while (!windowLog.isEmpty() && windowLog.peek() < boundary) {
            windowLog.poll();
        }

        if (windowLog.size() < limit) {
            windowLog.add(now);
            return new RateLimitResponse(true, 0, windowLog.size());
        }

        // Calculate Retry-After based on the oldest timestamp in the log
        long oldestTimestamp = windowLog.peek();
        long retryAfterMs = oldestTimestamp + windowMillis - now;
        return new RateLimitResponse(false, (long) Math.ceil(retryAfterMs / 1000.0), windowLog.size());
    }
}