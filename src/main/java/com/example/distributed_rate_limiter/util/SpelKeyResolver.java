package com.example.distributed_rate_limiter.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility to resolve dynamic keys from the HttpServletRequest using SpEL.
 * Includes an internal cache to avoid re-parsing identical SpEL strings.
 */
@Component
public class SpelKeyResolver {

    private final ExpressionParser parser = new SpelExpressionParser();

    // Performance optimization: Cache parsed expressions
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * Resolves the key from the request.
     * * @param expressionStr The SpEL string (e.g., "#request.getHeader('X-User-ID')")
     * @param request The current HTTP request
     * @return The resolved string key, or the remote IP address as a fallback.
     */
    public String resolve(String expressionStr, HttpServletRequest request) {
        // Fallback to IP if no expression is provided
        if (expressionStr == null || expressionStr.trim().isEmpty()) {
            return request.getRemoteAddr();
        }

        try {
            // 1. Get or compile the expression
            Expression expression = expressionCache.computeIfAbsent(expressionStr, parser::parseExpression);

            // 2. Set up the evaluation context with the 'request' variable
            StandardEvaluationContext context = new StandardEvaluationContext();
            context.setVariable("request", request);

            // 3. Evaluate the expression against the context
            String result = expression.getValue(context, String.class);

            // 4. Return result or fallback if evaluation returns null
            return (result != null) ? result : request.getRemoteAddr();

        } catch (Exception e) {
            // In a library, we should be resilient.
            // If the user writes a bad SpEL expression, we fallback to IP
            // rather than failing the whole request.
            return request.getRemoteAddr();
        }
    }
}