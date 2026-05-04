package com.example.distributed_rate_limiter.annotation;

import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * The SpEL expression to resolve the rate-limiting key.
     * Example: "#request.getHeader('X-User-ID')" or "#request.remoteAddr"
     */
    String keyExpression() default "";

    /**
     * The maximum number of requests/tokens allowed.
     */
    long limit() default -1; // -1 indicates "use global default"

    /**
     * The window size or refill interval.
     */
    long window() default -1;

    /**
     * The time unit for the window (Seconds, Minutes, etc.)
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * The algorithm to use for this specific API/Controller.
     */
    RateLimitAlgorithm strategy() default RateLimitAlgorithm.DEFAULT;

    /**
     * Whether this API's traffic should also be counted toward the Global Rate Limit.
     */
    boolean includeInGlobal() default true;
}