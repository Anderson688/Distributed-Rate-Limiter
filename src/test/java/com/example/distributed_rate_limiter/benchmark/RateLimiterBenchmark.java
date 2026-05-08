package com.example.distributed_rate_limiter.benchmark;

import com.example.distributed_rate_limiter.annotation.RateLimit;
import com.example.distributed_rate_limiter.aspect.RateLimitAspect;
import com.example.distributed_rate_limiter.config.RateLimitProperties;
import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;
import com.example.distributed_rate_limiter.strategy.inmemory.InMemoryRateLimitStrategy;
import com.example.distributed_rate_limiter.util.SpelKeyResolver;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime) // Measure the average time it takes to process one request
@OutputTimeUnit(TimeUnit.MICROSECONDS) // Display results in Microseconds (1 ms = 1000 us)
@Warmup(iterations = 3, time = 1) // 3 iterations to warm up the JVM JIT compiler
@Measurement(iterations = 5, time = 1) // 5 iterations to actually measure performance
@Fork(1) // Run in 1 isolated JVM
public class RateLimiterBenchmark {

    private RateLimitAspect aspect;
    private ProceedingJoinPoint mockJoinPoint;

    @Setup
    public void setup() throws Throwable {
        // 1. Initialize your actual library components (No Spring Context overhead!)
        RateLimitProperties props = new RateLimitProperties();
        SpelKeyResolver resolver = new SpelKeyResolver();

        // We test the In-Memory strategy here to measure the pure CPU overhead
        // of your library (AOP + SpEL + Math) without Network Latency.
        InMemoryRateLimitStrategy strategy = new InMemoryRateLimitStrategy();

        aspect = new RateLimitAspect(strategy, props, resolver);

        // 2. Mock a realistic HTTP Request
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.100");
        request.addHeader("X-API-KEY", "benchmark-user");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // 3. Mock the AOP JoinPoint to simulate intercepting a controller method
        mockJoinPoint = Mockito.mock(ProceedingJoinPoint.class);
        MethodSignature signature = Mockito.mock(MethodSignature.class);

        Mockito.when(mockJoinPoint.getSignature()).thenReturn(signature);
        Mockito.when(signature.toShortString()).thenReturn("DummyController.testMethod()");

        // Point the mock to our DummyController below so it can read the @RateLimit annotation
        Mockito.when(signature.getMethod()).thenReturn(DummyController.class.getMethod("testMethod"));
        Mockito.when(mockJoinPoint.getTarget()).thenReturn(new DummyController());

        // Return a dummy value so the Aspect finishes successfully
        Mockito.when(mockJoinPoint.proceed()).thenReturn("OK");
    }

    @Benchmark
    public void benchmarkLibraryOverhead(Blackhole blackhole) throws Throwable {
        // Execute the aspect and consume the result in a Blackhole
        // to prevent Java's Dead-Code Elimination from optimizing the test away
        Object result = aspect.applyRateLimit(mockJoinPoint);
        blackhole.consume(result);
    }

    // A dummy class purely to provide the @RateLimit annotation for the Aspect to read
    public static class DummyController {
        // High limit so we don't actually trigger the RateLimitExceededException during the benchmark loop
        @RateLimit(limit = 10000000, window = 1, timeUnit = TimeUnit.HOURS, strategy = RateLimitAlgorithm.TOKEN_BUCKET)
        public void testMethod() {
            // Simulated business logic
        }
    }
}