package com.example.distributed_rate_limiter.strategy.inmemory;

import com.example.distributed_rate_limiter.strategy.RateLimitResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FixedWindowProcessorTest {

    @Test
    @DisplayName("Should allow requests up to the limit and then deny")
    void testFixedWindowLimits() {
        FixedWindowProcessor processor = new FixedWindowProcessor();

        long limit = 2;
        long window = 10;
        TimeUnit unit = TimeUnit.SECONDS;

        // 1st Request: Allowed (Count = 1)
        RateLimitResponse r1 = processor.process(limit, window, unit);
        assertTrue(r1.isAllowed());
        assertEquals(1, r1.getCurrentUsage());

        // 2nd Request: Allowed (Count = 2)
        RateLimitResponse r2 = processor.process(limit, window, unit);
        assertTrue(r2.isAllowed());
        assertEquals(2, r2.getCurrentUsage());

        // 3rd Request: Denied (Over limit)
        RateLimitResponse r3 = processor.process(limit, window, unit);
        assertFalse(r3.isAllowed());

        // Ensure Retry-After is correctly calculated (should be > 0 and <= 10)
        assertTrue(r3.getRetryAfterSeconds() > 0 && r3.getRetryAfterSeconds() <= 10);
    }
}