package com.example.distributed_rate_limiter.config;

import com.example.distributed_rate_limiter.aspect.RateLimitAspect;
import com.example.distributed_rate_limiter.strategy.inmemory.InMemoryRateLimitStrategy;
import com.example.distributed_rate_limiter.strategy.RateLimitStrategy;
import com.example.distributed_rate_limiter.strategy.redis.RedisRateLimitStrategy;
import com.example.distributed_rate_limiter.util.SpelKeyResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Auto-configuration for the Distributed Rate Limiter.
 * proxyBeanMethods = false is a performance optimization for configuration classes
 * that do not have inter-bean dependencies within the class.
 */
@AutoConfiguration
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(prefix = "ratelimiter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SpelKeyResolver spelKeyResolver() {
        return new SpelKeyResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimitStrategy strategy,
                                           RateLimitProperties properties,
                                           SpelKeyResolver keyResolver) {
        return new RateLimitAspect(strategy, properties, keyResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitExceptionHandler rateLimitExceptionHandler() {
        return new RateLimitExceptionHandler();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "ratelimiter", name = "store-type", havingValue = "redis")
    @ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
    static class RedisConfiguration {
        @Bean
        public RateLimitStrategy redisRateLimitStrategy(StringRedisTemplate redisTemplate) {
            return new RedisRateLimitStrategy(redisTemplate);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "ratelimiter", name = "store-type", havingValue = "in_memory", matchIfMissing = true)
    static class InMemoryConfiguration {
        @Bean
        public RateLimitStrategy inMemoryRateLimitStrategy() {
            return new InMemoryRateLimitStrategy();
        }
    }
}