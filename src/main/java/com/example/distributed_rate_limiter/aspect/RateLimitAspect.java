package com.example.distributed_rate_limiter.aspect;

import com.example.distributed_rate_limiter.annotation.RateLimit;
import com.example.distributed_rate_limiter.config.RateLimitProperties;
import com.example.distributed_rate_limiter.exception.RateLimitExceededException;
import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;
import com.example.distributed_rate_limiter.strategy.RateLimitResponse;
import com.example.distributed_rate_limiter.strategy.RateLimitStrategy;
import com.example.distributed_rate_limiter.util.SpelKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

@Aspect
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final RateLimitStrategy strategy;
    private final RateLimitProperties properties;
    private final SpelKeyResolver keyResolver;

    @Before("@within(com.example.distributed_rate_limiter.annotation.RateLimit) || @annotation(com.example.distributed_rate_limiter.annotation.RateLimit)")
    public void applyRateLimit(JoinPoint joinPoint) {
        if (!properties.isEnabled()) return;

        HttpServletRequest request = getCurrentRequest();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        // 1. Resolve Annotations
        RateLimit methodLevel = signature.getMethod().getAnnotation(RateLimit.class);
        RateLimit classLevel = joinPoint.getTarget().getClass().getAnnotation(RateLimit.class);

        // Priority: Method > Class
        RateLimit activeLimit = (methodLevel != null) ? methodLevel : classLevel;
        if (activeLimit == null) return;

        // 2. Global Check (Req 5, 12)
        if (properties.isGlobalEnabled() && activeLimit.includeInGlobal()) {
            executeCheck(request, "GLOBAL",
                    properties.getGlobalKeyExpression(),
                    properties.getGlobalLimit(),
                    properties.getGlobalWindow(),
                    properties.getGlobalTimeUnit(),
                    properties.getGlobalStrategy());
        }

        // 3. Local Check (Resolving parameters via precedence Req 11)
        executeCheck(request, "LOCAL",
                resolveValue(methodLevel != null ? methodLevel.keyExpression() : "", classLevel != null ? classLevel.keyExpression() : "", properties.getDefaultKeyExpression()),
                resolveValue(methodLevel != null ? methodLevel.limit() : -1, classLevel != null ? classLevel.limit() : -1, properties.getDefaultLimit()),
                resolveValue(methodLevel != null ? methodLevel.window() : -1, classLevel != null ? classLevel.window() : -1, properties.getDefaultWindow()),
                resolveValue(methodLevel != null ? methodLevel.timeUnit() : null, classLevel != null ? classLevel.timeUnit() : null, properties.getDefaultTimeUnit()),
                resolveValue(methodLevel != null ? methodLevel.strategy() : RateLimitAlgorithm.DEFAULT, classLevel != null ? classLevel.strategy() : RateLimitAlgorithm.DEFAULT, properties.getDefaultStrategy())
        );
    }

    private void executeCheck(HttpServletRequest request, String scope, String keyExpr,
                              long limit, long window, TimeUnit unit, RateLimitAlgorithm algo) {

        String identifier = keyResolver.resolve(keyExpr, request);
        // Key Pattern: rl:{serviceId}:{scope}:{id}
        String compositeKey = String.format("rl:%s:%s:%s", properties.getServiceId(), scope, identifier);

        try {
            RateLimitResponse response = strategy.check(compositeKey, limit, window, unit, algo);

            if (!response.isAllowed()) {
                throw new RateLimitExceededException(
                        "Rate limit exceeded for " + scope, compositeKey,
                        limit, window, unit, response.getRetryAfterSeconds()
                );
            }
        } catch (Exception e) {
            if (e instanceof RateLimitExceededException) throw e;
            // Req 15: Handle Redis unreachable (Fail-Open logic)
            if (properties.isFailOpen()) {
                log.warn("Rate Limiter Storage is down! Failing OPEN for key: {}", compositeKey, e);
            } else {
                throw new RuntimeException("Rate Limiter Service Unavailable", e);
            }
        }
    }

    // Helper to handle the Precedence logic: API > Controller > Default
    private <T> T resolveValue(T methodVal, T classVal, T defaultVal) {
        if (isProvided(methodVal)) return methodVal;
        if (isProvided(classVal)) return classVal;
        return defaultVal;
    }

    private boolean isProvided(Object val) {
        if (val == null) return false;
        if (val instanceof String) return !((String) val).isEmpty();
        if (val instanceof Long || val instanceof Integer) return ((Number) val).longValue() != -1;
        if (val instanceof RateLimitAlgorithm) return val != RateLimitAlgorithm.DEFAULT;
        return true;
    }

    private HttpServletRequest getCurrentRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }
}