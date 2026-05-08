package com.example.distributed_rate_limiter.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpelKeyResolverTest {

    private SpelKeyResolver keyResolver;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        keyResolver = new SpelKeyResolver();
        request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
    }

    @Test
    @DisplayName("Should fallback to IP Address if expression is empty")
    void testEmptyExpressionFallback() {
        String result = keyResolver.resolve("", request);
        assertEquals("10.0.0.5", result);
    }

    @Test
    @DisplayName("Should resolve custom HTTP headers using SpEL")
    void testResolveHeader() {
        request.addHeader("X-API-KEY", "super-secret-key");
        String result = keyResolver.resolve("#request.getHeader('X-API-KEY')", request);
        assertEquals("super-secret-key", result);
    }

    @Test
    @DisplayName("Should fallback to IP Address if SpEL expression is invalid")
    void testInvalidSpelFallback() {
        // Malformed SpEL
        String result = keyResolver.resolve("#request.getSomethingThatDoesNotExist()", request);
        assertEquals("10.0.0.5", result);
    }
}