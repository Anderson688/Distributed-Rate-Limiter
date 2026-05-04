package com.example.distributed_rate_limiter.exception;

import java.util.concurrent.TimeUnit;

public class RateLimitExceededException extends RuntimeException {

    private final String key;
    private final long limit;
    private final long window;
    private final TimeUnit timeUnit;
    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, String key, long limit,
                                      long window, TimeUnit timeUnit, long retryAfterSeconds) {
        super(message);
        this.key = key;
        this.limit = limit;
        this.window = window;
        this.timeUnit = timeUnit;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getKey() { return key; }
    public long getLimit() { return limit; }
    public long getWindow() { return window; }
    public TimeUnit getTimeUnit() { return timeUnit; }
    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
