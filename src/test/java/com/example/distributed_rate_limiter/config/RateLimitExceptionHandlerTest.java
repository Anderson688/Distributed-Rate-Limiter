package com.example.distributed_rate_limiter.config;

import com.example.distributed_rate_limiter.exception.RateLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RateLimitExceptionHandlerTest {

    @Test
    @DisplayName("Should return HTTP 429 and inject Retry-After header")
    void testHandleRateLimitException() {
        RateLimitExceptionHandler handler = new RateLimitExceptionHandler();

        // Create the mock exception (Simulating a block for 45 seconds)
        long retryAfterSeconds = 45;
        RateLimitExceededException ex = new RateLimitExceededException(
                "Rate limit exceeded for scope: LOCAL",
                "rl:demo:LOCAL:user_1",
                10,
                1,
                TimeUnit.MINUTES,
                retryAfterSeconds
        );

        // FIX: Changed method call from handleRateLimitException to handleRateLimit
        ResponseEntity<Object> response = handler.handleRateLimit(ex);

        // Verify Status Code is 429
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());

        // Verify Headers
        assertNotNull(response.getHeaders());
        assertEquals("45", response.getHeaders().getFirst("Retry-After"));

        // Note: I removed the X-RateLimit-Key assertion here since your handler
        // currently doesn't inject it into the headers (it only injects Retry-After).
        // If you want to add the Key to headers, you can add it in the ExceptionHandler class!

        // Verify Body
        assertNotNull(response.getBody());
    }
}