package com.example.distributed_rate_limiter;

import com.example.distributed_rate_limiter.annotation.RateLimit;
import com.example.distributed_rate_limiter.config.RateLimitAutoConfiguration;
import com.example.distributed_rate_limiter.exception.RateLimitExceededException;
import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = {
                RateLimitAutoConfiguration.class,
                DistributedRateLimiterIntegrationTest.TestApplication.class,
                DistributedRateLimiterIntegrationTest.DummyService.class
        },
        properties = {
                "ratelimiter.enabled=true",
                "ratelimiter.store-type=in_memory",
                "ratelimiter.service-id=test-service"
        }
)
class DistributedRateLimiterIntegrationTest {

    @Autowired
    private DummyService dummyService;

    @BeforeEach
    void setUp() {
        // Mock an incoming HTTP request since our Aspect relies on it for the IP address
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        // Clean up the thread-local context after each test
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Should allow requests under the limit and block the one that exceeds it")
    void testRateLimitEnforced() {
        // 1. First 3 requests should pass flawlessly
        assertDoesNotThrow(() -> dummyService.limitedAction());
        assertDoesNotThrow(() -> dummyService.limitedAction());
        assertDoesNotThrow(() -> dummyService.limitedAction());

        // 2. The 4th request MUST throw the exception
        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> dummyService.limitedAction()
        );

        // 3. Verify the exception contains the correct metadata
        assertEquals(3, exception.getLimit());
        assertTrue(exception.getRetryAfterSeconds() > 0, "Retry time should be calculated");
        assertTrue(exception.getKey().contains("192.168.1.100"), "Key should contain the mocked IP address");
    }

    @Test
    @DisplayName("Should allow unlimited requests to a method without the annotation")
    void testNoRateLimitOnStandardMethod() {
        // Loop 10 times, well over the limit of 3
        for (int i = 0; i < 10; i++) {
            assertDoesNotThrow(() -> dummyService.unlimitedAction());
        }
    }

    // --- Mocks and Configurations for the Test Environment ---

    /**
     * A dummy Spring application to load the context.
     */
    @SpringBootApplication
    static class TestApplication {
    }

    /**
     * A dummy service to apply our AOP annotations to.
     */
    @Service
    static class DummyService {

        @RateLimit(limit = 3, window = 10, timeUnit = TimeUnit.SECONDS, strategy = RateLimitAlgorithm.FIXED_WINDOW)
        public String limitedAction() {
            return "Success!";
        }

        public String unlimitedAction() {
            return "Always Success!";
        }
    }
}