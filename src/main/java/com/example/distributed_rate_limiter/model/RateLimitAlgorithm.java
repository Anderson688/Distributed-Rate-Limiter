package com.example.distributed_rate_limiter.model;

public enum RateLimitAlgorithm {
    TOKEN_BUCKET,
    SLIDING_WINDOW,
    FIXED_WINDOW,
    DEFAULT // Uses the algorithm defined in application.properties
}