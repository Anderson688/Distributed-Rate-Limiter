package com.example.distributed_rate_limiter.config;

import com.example.distributed_rate_limiter.aspect.RateLimitAspect;
import com.example.distributed_rate_limiter.strategy.inmemory.InMemoryRateLimitStrategy;
import com.example.distributed_rate_limiter.strategy.redis.RedisRateLimitStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class));

    @Test
    @DisplayName("Should load InMemory strategy by default if no store-type is specified")
    void shouldLoadInMemoryStrategyByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(InMemoryRateLimitStrategy.class);
            assertThat(context).doesNotHaveBean(RedisRateLimitStrategy.class);
            assertThat(context).hasSingleBean(RateLimitAspect.class);
        });
    }

    @Test
    @DisplayName("Should load Redis strategy when store-type is redis")
    void shouldLoadRedisStrategyWhenConfigured() {
        contextRunner
                // FIX: Matched the exact prefix "ratelimiter" and lowercase value "redis"
                .withPropertyValues("ratelimiter.store-type=redis")
                .withUserConfiguration(MockRedisConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisRateLimitStrategy.class);
                    assertThat(context).doesNotHaveBean(InMemoryRateLimitStrategy.class);
                });
    }

    @Test
    @DisplayName("Should completely disable the library if ratelimiter.enabled is false")
    void shouldNotLoadBeansWhenDisabled() {
        contextRunner
                // FIX: Matched the exact prefix "ratelimiter"
                .withPropertyValues("ratelimiter.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RateLimitAspect.class);
                    assertThat(context).doesNotHaveBean(RedisRateLimitStrategy.class);
                    assertThat(context).doesNotHaveBean(InMemoryRateLimitStrategy.class);
                });
    }

    @Configuration
    static class MockRedisConfiguration {
        @Bean
        public StringRedisTemplate stringRedisTemplate() {
            return Mockito.mock(StringRedisTemplate.class);
        }
    }
}