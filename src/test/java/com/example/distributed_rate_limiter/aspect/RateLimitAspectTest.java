package com.example.distributed_rate_limiter.aspect;

import com.example.distributed_rate_limiter.annotation.RateLimit;
import com.example.distributed_rate_limiter.config.RateLimitProperties;
import com.example.distributed_rate_limiter.exception.RateLimitExceededException;
import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;
import com.example.distributed_rate_limiter.strategy.RateLimitResponse;
import com.example.distributed_rate_limiter.strategy.RateLimitStrategy;
import com.example.distributed_rate_limiter.util.SpelKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RateLimitAspectTest {

    private RateLimitStrategy strategy;
    private RateLimitProperties properties;
    private SpelKeyResolver keyResolver;
    private RateLimitAspect aspect;
    private ProceedingJoinPoint joinPoint;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        strategy = mock(RateLimitStrategy.class);
        properties = new RateLimitProperties(); // Using real properties object for defaults
        keyResolver = mock(SpelKeyResolver.class);
        aspect = new RateLimitAspect(strategy, properties, keyResolver);

        // Setup HTTP Request Context
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // Mock AOP JoinPoint
        joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("TestController.testMethod()");

        // Use reflection to mock a method that has the @RateLimit annotation
        when(signature.getMethod()).thenReturn(DummyController.class.getMethod("testMethod"));
        when(joinPoint.getTarget()).thenReturn(new DummyController());

        when(keyResolver.resolve(any(), any())).thenReturn("test-user");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("Should proceed if rate limit is NOT exceeded")
    void testProceedsWhenAllowed() throws Throwable {
        when(strategy.check(any(), anyLong(), anyLong(), any(), any()))
                .thenReturn(new RateLimitResponse(true, 0, 1));

        aspect.applyRateLimit(joinPoint);

        // Verify the underlying controller method was actually called
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    @DisplayName("Should throw custom exception if rate limit IS exceeded")
    void testThrowsExceptionWhenDenied() throws Throwable { // <-- ADDED THIS
        when(strategy.check(any(), anyLong(), anyLong(), any(), any()))
                .thenReturn(new RateLimitResponse(false, 5, 10));

        assertThrows(RateLimitExceededException.class, () -> aspect.applyRateLimit(joinPoint));

        // Verify the controller method was BLOCKED
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("Fail-Open: Should proceed if strategy throws unexpected exception")
    void testFailOpenMechanism() throws Throwable {
        // Simulate a Redis crash
        when(strategy.check(any(), anyLong(), anyLong(), any(), any()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        // Ensure Fail-Open is enabled
        properties.setFailOpen(true);

        // Should NOT throw an exception, it should swallow it and proceed
        assertDoesNotThrow(() -> aspect.applyRateLimit(joinPoint));
        verify(joinPoint, times(1)).proceed();
    }

    // Dummy class purely to provide a Reflection target for the AnnotationUtils
    static class DummyController {
        @RateLimit(limit = 5, window = 1, timeUnit = TimeUnit.MINUTES, strategy = RateLimitAlgorithm.TOKEN_BUCKET)
        public void testMethod() {}
    }
}