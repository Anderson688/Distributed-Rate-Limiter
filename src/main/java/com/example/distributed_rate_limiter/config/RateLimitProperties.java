package com.example.distributed_rate_limiter.config;

import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.concurrent.TimeUnit;

/**
 * Configuration properties for the Rate Limiter library.
 * Clients can override these in application.properties/yml.
 */
@Data
@ConfigurationProperties(prefix = "ratelimiter")
public class RateLimitProperties {

    /** Whether to enable the rate limiter library globally. */
    private boolean enabled = true;

    /** Choose between REDIS (distributed) or IN_MEMORY (standalone). */
    private StoreType storeType = StoreType.IN_MEMORY;

    /** Unique ID for the service to prevent key collisions in Redis. */
    private String serviceId = "default";

    /** If Store is down, should we allow the request (true) or block (false). */
    private boolean failOpen = true;

    // --- Global Rate Limit Settings ---

    /** Whether a global quota check should be performed for every request. */
    private boolean globalEnabled = false;

    /** Default SpEL expression for the global key (e.g., User ID or IP). */
    private String globalKeyExpression = "#request.remoteAddr";

    private long globalLimit = 1000;
    private long globalWindow = 1;
    private TimeUnit globalTimeUnit = TimeUnit.HOURS;
    private RateLimitAlgorithm globalStrategy = RateLimitAlgorithm.TOKEN_BUCKET;

    // --- Default API Level Settings (Fallback for Requirement 11) ---

    private String defaultKeyExpression = "#request.remoteAddr";
    private long defaultLimit = 10;
    private long defaultWindow = 1;
    private TimeUnit defaultTimeUnit = TimeUnit.MINUTES;
    private RateLimitAlgorithm defaultStrategy = RateLimitAlgorithm.FIXED_WINDOW;

    public enum StoreType {
        REDIS, IN_MEMORY
    }
}