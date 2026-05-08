package com.example.distributed_rate_limiter.strategy.inmemory;

import com.example.distributed_rate_limiter.strategy.RateLimitResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TokenBucketProcessorTest {

    @Test
    @DisplayName("Should drain bucket and then deny requests")
    void testTokenBucketDrain() {
        TokenBucketProcessor processor = new TokenBucketProcessor();

        // Limit: 2 tokens per 10 seconds
        long limit = 2;
        long window = 10;
        TimeUnit unit = TimeUnit.SECONDS;

        // 1st Request - Should pass (1 token left)
        RateLimitResponse r1 = processor.process(limit, window, unit);
        assertTrue(r1.isAllowed());

        // 2nd Request - Should pass (0 tokens left)
        RateLimitResponse r2 = processor.process(limit, window, unit);
        assertTrue(r2.isAllowed());

        // 3rd Request - Should Fail (Bucket empty)
        RateLimitResponse r3 = processor.process(limit, window, unit);
        assertFalse(r3.isAllowed());
        assertTrue(r3.getRetryAfterSeconds() > 0);
    }
}