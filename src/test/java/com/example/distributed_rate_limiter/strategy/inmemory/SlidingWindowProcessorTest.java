package com.example.distributed_rate_limiter.strategy.inmemory;

import com.example.distributed_rate_limiter.strategy.RateLimitResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SlidingWindowProcessorTest {

    @Test
    @DisplayName("Should maintain an exact sliding window queue and deny excess requests")
    void testSlidingWindowLimits() {
        SlidingWindowProcessor processor = new SlidingWindowProcessor();

        long limit = 3;
        long window = 5;
        TimeUnit unit = TimeUnit.SECONDS;

        // Fire 3 quick requests (Should all be allowed)
        assertTrue(processor.process(limit, window, unit).isAllowed());
        assertTrue(processor.process(limit, window, unit).isAllowed());
        RateLimitResponse r3 = processor.process(limit, window, unit);

        assertTrue(r3.isAllowed());
        assertEquals(3, r3.getCurrentUsage());

        // 4th Request (Should be denied because the 5-second window hasn't passed)
        RateLimitResponse r4 = processor.process(limit, window, unit);

        assertFalse(r4.isAllowed());
        assertTrue(r4.getRetryAfterSeconds() > 0);
    }
}