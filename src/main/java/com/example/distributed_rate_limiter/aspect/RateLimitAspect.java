package com.example.distributed_rate_limiter.aspect;

import com.example.distributed_rate_limiter.annotation.RateLimit;
import com.example.distributed_rate_limiter.config.RateLimitProperties;
import com.example.distributed_rate_limiter.exception.RateLimitExceededException;
import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;
import com.example.distributed_rate_limiter.strategy.RateLimitResponse;
import com.example.distributed_rate_limiter.strategy.RateLimitStrategy;
import com.example.distributed_rate_limiter.util.SpelKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Aspect
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RateLimitStrategy strategy;
    private final RateLimitProperties properties;
    private final SpelKeyResolver keyResolver;

    public RateLimitAspect(RateLimitStrategy strategy,
                           RateLimitProperties properties,
                           SpelKeyResolver keyResolver) {
        this.strategy = strategy;
        this.properties = properties;
        this.keyResolver = keyResolver;
    }

    @Around("@within(com.example.distributed_rate_limiter.annotation.RateLimit) || @annotation(com.example.distributed_rate_limiter.annotation.RateLimit)")
    public Object applyRateLimit(ProceedingJoinPoint joinPoint) throws Throwable {

        // 1. If rate limiting is completely disabled, just proceed
        if (!properties.isEnabled()) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Check if the method has the annotation first (Method overrides Class)
        RateLimit rateLimitAnnotation = AnnotationUtils.findAnnotation(method, RateLimit.class);

        // If the method doesn't have it, check if the Controller class has it
        if (rateLimitAnnotation == null) {
            rateLimitAnnotation = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RateLimit.class);
        }

        // Failsafe: If somehow we triggered this aspect but no annotation exists
        if (rateLimitAnnotation == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = getHttpServletRequest();
        if (request == null) {
            log.warn("Could not obtain HttpServletRequest. Skipping rate limit check.");
            return joinPoint.proceed();
        }

        // 2. Check Global Rate Limit (If enabled in application.yml)
        if (properties.isGlobalEnabled()) {
            executeCheck(
                    request,
                    joinPoint,
                    "GLOBAL",
                    properties.getGlobalKeyExpression(),
                    properties.getGlobalLimit(),
                    properties.getGlobalWindow(),
                    properties.getGlobalTimeUnit(),
                    properties.getGlobalStrategy()
            );
        }

        // 3. Resolve API-Level (Local) configurations, with fallbacks to defaults
        String keyExpr = rateLimitAnnotation.keyExpression().isEmpty() ? properties.getDefaultKeyExpression() : rateLimitAnnotation.keyExpression();
        long limit = rateLimitAnnotation.limit() < 0 ? properties.getDefaultLimit() : rateLimitAnnotation.limit();
        long window = rateLimitAnnotation.window() < 0 ? properties.getDefaultWindow() : rateLimitAnnotation.window();
        TimeUnit timeUnit = rateLimitAnnotation.timeUnit() == TimeUnit.NANOSECONDS ? properties.getDefaultTimeUnit() : rateLimitAnnotation.timeUnit();
        RateLimitAlgorithm algo = rateLimitAnnotation.strategy() == RateLimitAlgorithm.DEFAULT ? properties.getDefaultStrategy() : rateLimitAnnotation.strategy();

        // 4. Check Local Rate Limit
        executeCheck(
                request,
                joinPoint,
                "LOCAL",
                keyExpr,
                limit,
                window,
                timeUnit,
                algo
        );

        // 5. If both checks pass, execute the actual controller method
        return joinPoint.proceed();
    }

    private void executeCheck(HttpServletRequest request,
                              ProceedingJoinPoint joinPoint,
                              String scope,
                              String keyExpr,
                              long limit,
                              long window,
                              TimeUnit unit,
                              RateLimitAlgorithm algo) {

        try {
            // Parse the SpEL expression (e.g., extracting IP or Header)
            String identifier = keyResolver.resolve(keyExpr, request);
            String compositeKey;

            // KEY GENERATION FIX: Isolate LOCAL APIs using the method signature
            if ("GLOBAL".equals(scope)) {
                compositeKey = String.format("rl:%s:%s:%s",
                        properties.getServiceId(),
                        scope,
                        identifier);
            } else {
                String methodName = joinPoint.getSignature().toShortString();
                compositeKey = String.format("rl:%s:%s:%s:%s",
                        properties.getServiceId(),
                        scope,
                        methodName,
                        identifier);
            }

            // Execute the strategy
            RateLimitResponse response = strategy.check(compositeKey, limit, window, unit, algo);

            if (!response.isAllowed()) {
                log.warn("Rate limit exceeded for scope: {}, key: {}. Retry after {} seconds.",
                        scope, compositeKey, response.getRetryAfterSeconds());

                throw new RateLimitExceededException(
                        String.format("Rate limit exceeded for scope: %s", scope),
                        compositeKey,
                        limit,
                        window,
                        unit,
                        response.getRetryAfterSeconds()
                );
            }

        } catch (RateLimitExceededException e) {
            // Re-throw our custom exception so the ExceptionHandler catches it
            throw e;
        } catch (Exception e) {
            // Fail-Open mechanism: If Redis crashes or SpEL fails, do we let traffic through?
            if (properties.isFailOpen()) {
                log.error("Rate limiter check failed, but fail-open is true. Allowing request. Error: {}", e.getMessage());
            } else {
                log.error("Rate limiter check failed and fail-open is false. Blocking request.");
                throw new RuntimeException("Internal rate limiter error", e);
            }
        }
    }

    private HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
}