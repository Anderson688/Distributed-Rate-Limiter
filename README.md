# Distributed Rate Limiter - Spring Boot Starter

A plug-and-play Spring Boot Starter library providing distributed, highly-concurrent rate limiting. Built to protect high-throughput systems using Redis and Lua scripting, with a seamless In-Memory fallback.

## Features
* **Zero-Config Setup:** Simply add the dependency and annotate your controllers.
* **Distributed & Thread-Safe:** Uses Redis Lua scripts to guarantee atomic operations across multiple application instances.
* **Multiple Algorithms:** Supports Token Bucket, Fixed Window, and Sliding Window algorithms.
* **SpEL Support:** Dynamically rate-limit based on IP, User ID, or custom HTTP headers using Spring Expression Language.
* **Fail-Open Resiliency:** Gracefully degrades to keep APIs online if the storage layer becomes unreachable.

## Installation

Since this library is not yet published to Maven Central, you can install it to your local Maven repository.

1. Clone the repository and install it:
```bash
mvn clean install
```

2. Add the dependency to your target Spring Boot application's `pom.xml`:
```xml
<dependency>
    <groupId>io.github.anderson688</groupId>
    <artifactId>distributed-rate-limiter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Choose Your Storage Engine

This library uses **optional dependencies** to keep your application lightweight. You must include the backing store dependency of your choice in your application's `pom.xml`:

### Option A: In-Memory (Great for Local Dev or Single-Instance Apps)

If you set `store-type: in_memory` in your configuration, you must add Caffeine Cache:

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

### Option B: Redis (Required for Distributed Microservices)

If you set `store-type: redis` in your configuration, you must add Spring Data Redis:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

## Quick Start

Annotate your Spring Web endpoints with `@RateLimit`:

```java
import com.example.distributed_rate_limiter.annotation.RateLimit;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.concurrent.TimeUnit;

@RestController
public class PaymentController {

    // Limit to 5 requests per minute per IP Address
    @RateLimit(limit = 5, window = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping("/pay")
    public String processPayment() {
        return "Payment Processed";
    }
}
```

## Class-Level vs. Method-Level Limiting

To keep your code clean and DRY (Don't Repeat Yourself), you can apply the `@RateLimit` annotation at either the **Class level**, the **Method level**, or both!

### 1. Class-Level (Protect all endpoints)
If all endpoints in a controller share the same rate limit policy, simply annotate the class. Every API inside will get its own isolated rate-limit bucket based on this configuration.

### 2. Method-Level Override (Surgical precision)
If you annotate a specific method, its configuration will **completely override** the class-level annotation. This is perfect for when 90% of your controller is standard traffic, but one specific endpoint (like a login or payment route) needs stricter limits.

### 3. Smart Fallbacks
You do not have to define every parameter in the annotation. If you omit a parameter (like `timeUnit` or `strategy`), the library will automatically fall back to the default settings you defined in your `application.yml`.

### Example Hierarchy:

```java
import com.example.distributed_rate_limiter.annotation.RateLimit;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.TimeUnit;

// 1. CLASS LEVEL: All APIs here allow 50 requests per minute by default
@RateLimit(limit = 50, window = 1, timeUnit = TimeUnit.MINUTES)
@RestController
@RequestMapping("/api/users")
public class UserController {

    // Inherits the Class-level limit (50 req / 1 min)
    @GetMapping("/profile")
    public String getProfile() {
        return "User Profile";
    }

    // Inherits the Class-level limit (50 req / 1 min)
    @GetMapping("/settings")
    public String getSettings() {
        return "User Settings";
    }

    // 2. METHOD OVERRIDE: This highly sensitive endpoint overrides the class 
    // and strictly limits traffic to 3 requests per hour!
    @RateLimit(limit = 3, window = 1, timeUnit = TimeUnit.HOURS)
    @PostMapping("/password-reset")
    public String resetPassword() {
        return "Password reset email sent";
    }

    // 3. SMART FALLBACK: Only defines the limit. The window, timeUnit, 
    // and strategy will seamlessly fall back to your application.yml defaults!
    @RateLimit(limit = 500)
    @GetMapping("/public-data")
    public String getPublicData() {
        return "Public Data";
    }
}
```

## Dynamic Key Resolution (SpEL)

By default, the rate limiter tracks usage based on the client's IP Address (`#request.remoteAddr`). However, you can rate-limit based on *anything* in the incoming HTTP request using Spring Expression Language (SpEL).

The `#request` variable exposes the underlying `HttpServletRequest` object, allowing you to extract headers, parameters, or paths dynamically.

### Examples:

**1. Rate Limit by Custom Header (e.g., API Key or User ID)**
```java
// Limit 100 requests per minute per specific API Key
@RateLimit(
        limit = 100,
        window = 1,
        timeUnit = TimeUnit.MINUTES,
        keyExpression = "#request.getHeader('X-API-Key')"
)
@GetMapping("/data")
public String getData() {
    return "Data payload";
}
```

**2. Rate Limit by Query Parameter**
```java
// Limit 5 requests per second for a specific tenant ID (?tenant_id=xyz)
@RateLimit(
    limit = 5, 
    window = 1, 
    timeUnit = TimeUnit.SECONDS, 
    keyExpression = "#request.getParameter('tenant_id')"
)
@GetMapping("/tenant-info")
public String getTenantInfo() {
    return "Tenant Info";
}
```

**3. Rate Limit by URI Path**
```java
// Limit requests based on the exact path being accessed
@RateLimit(
    limit = 10, 
    window = 1, 
    timeUnit = TimeUnit.HOURS, 
    keyExpression = "#request.getRequestURI()"
)
@GetMapping("/expensive-operation/{id}")
public String doExpensiveMath() {
    return "Calculated";
}
```

## Supported Algorithms

Not all endpoints have the same traffic patterns. This library allows you to choose the exact rate-limiting algorithm that fits your specific use case.

### Available Strategies (`RateLimitAlgorithm` enum):
* **`TOKEN_BUCKET`** *(Recommended)*: Best for APIs that need to handle smooth, consistent traffic while allowing for occasional bursts.
* **`FIXED_WINDOW`**: Best for strict quotas (e.g., "100 API calls per day"). It is highly performant but susceptible to traffic spikes at the edge of the window.
* **`SLIDING_WINDOW`**: The most accurate algorithm. Best for critical endpoints (like payments or logins) where you need to strictly prevent abuse without the edge-spike flaws of a fixed window.

### Setting the Global Default
You can define the default algorithm for all annotated endpoints in your `application.yml`:
```yaml
ratelimiter:
  default-strategy: token_bucket # Options: token_bucket, fixed_window, sliding_window
```

### API-Level Overrides

You can easily override the default strategy for specific, highly-sensitive endpoints directly inside the `@RateLimit` annotation using the strategy parameter:
```java
import com.example.distributed_rate_limiter.model.RateLimitAlgorithm;

@RestController
public class AuthController {

    // Login is highly sensitive, so we use the strict Sliding Window
    @RateLimit(
        limit = 5, 
        window = 1, 
        timeUnit = TimeUnit.MINUTES, 
        strategy = RateLimitAlgorithm.SLIDING_WINDOW
    )
    @PostMapping("/login")
    public String authenticate() {
        return "Token generated";
    }

    // Standard data fetching can fall back to the default application.yml strategy
    @RateLimit(limit = 100, window = 1, timeUnit = TimeUnit.MINUTES)
    @GetMapping("/data")
    public String fetchData() {
        return "Here is your data";
    }
}
```

## Configuration Reference

You can customize the rate limiter by adding these properties to your `application.yml` or `application.properties`. Below are all available configurations along with their default values:
```yaml
ratelimiter:
  # --- Core Settings ---
  enabled: true                 # Globally enable or disable the rate limiter
  store-type: in_memory         # Options: 'redis' (distributed) or 'in_memory' (local)
  service-id: default           # Unique ID for this service to prevent Redis key collisions
  fail-open: true               # If true, allows traffic to pass if Redis goes offline

  # --- Global Rate Limit Settings ---
  # If enabled, applies a blanket rate limit across all requests to your application.
  global-enabled: false
  global-key-expression: "#request.remoteAddr" # Groups traffic by IP by default
  global-limit: 1000
  global-window: 1
  global-time-unit: hours
  global-strategy: token_bucket

  # --- Default API Settings ---
  # Fallback values used when an endpoint has @RateLimit but is missing specific parameters.
  default-key-expression: "#request.remoteAddr"
  default-limit: 10
  default-window: 1
  default-time-unit: minutes
  default-strategy: fixed_window
```

## Performance & Overhead

This library is designed for high-concurrency environments and introduces near-zero latency to the Tomcat event loop. Performance is continually verified using the **Java Microbenchmark Harness (JMH)** to bypass JIT compilation and JVM warmup inconsistencies.

### JMH Microbenchmark Results
The internal overhead of the library (AOP interception, Annotation reflection, SpEL AST caching, and In-Memory Token Bucket math) is strictly measured without network RTT.

| Benchmark | Mode | Cnt | Score | Error | Units |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `benchmarkLibraryOverhead` | avgt | 5 | **20.910** | ± 6.659 | us/op |

* **Average Overhead:** `~0.02 milliseconds` (20.9 microseconds) per request.
* **SpEL Optimization:** The `SpelKeyResolver` caches the Abstract Syntax Tree (AST) of parsed expressions in a `ConcurrentHashMap`. After the first execution, parsing overhead drops to zero.

### Running the Benchmarks Locally
To verify these numbers on your own hardware, clone the repository and execute the benchmark profile (this process takes about 3-5 minutes to complete JIT warmup iterations):
```bash
./mvnw clean test -P benchmark
```

### Throughput & Scalability (Redis)
When using the `redis` store type, maximum throughput is dictated entirely by your network I/O, connection pool settings (Lettuce/Jedis), and your Redis topology.

Below is an approximate throughput matrix demonstrating how the library scales under load (Estimated for bare-metal Linux / AWS EC2 deployments):

| Redis Topology | Scenario | Est. Max RPS | Primary Bottleneck |
| :--- | :--- | :--- | :--- |
| **Single Node** | Global Limit (All traffic hits 1 key) | ~25,000 - 30,000 | Redis Single-Threaded Event Loop |
| **Single Node** | Unique Keys (e.g., Limit by IP) | ~40,000 - 50,000 | Redis CPU / Tomcat Connection Pool |
| **3-Node Cluster** | Global Limit (All traffic hits 1 key) | ~25,000 - 30,000 | Hash Slot constraint (Single Master Node) |
| **3-Node Cluster**| Unique Keys (e.g., Limit by IP) | 100,000+ (Scales Linearly) | Network Bandwidth |

> **Note on Local Benchmarking:** If you run these load tests locally using Docker Desktop on macOS, your maximum throughput will bottleneck around **~18,000 - 20,000 RPS** across all scenarios. This is a known physical limit of the Docker macOS virtualized network bridge, not a limitation of the library or Redis.

**Scaling Advice for Enterprise Clients:**
1. **Connection Pooling:** Ensure your Spring Boot `spring.data.redis.lettuce.pool.max-active` is set high enough to prevent Tomcat thread starvation under heavy load.
2. **Key Distribution:** To take advantage of a Redis Cluster, rate-limit by unique identifiers (`#request.remoteAddr` or `#request.getHeader('X-User-ID')`). This allows Redis to shard the Lua script executions across multiple master nodes.

### Running the Load Tests Locally
To verify the system architecture bottlenecks yourself, you can use the included Docker Compose load-testing environment.

1. Ensure the library is installed locally: `mvn clean install`
2. Spin up the App Server and Redis containers:
   ```bash
   docker-compose up -d --build
   ```
3. Test Scenario 1 (Hot Key Bottleneck):
   ```bash
   docker-compose run --rm load-generator sh -c "apk add --no-cache wrk && wrk -t12 -c400 -d30s http://app-server:8080/api/global-limited"
   ```
4. Test Scenario 2 (Distributed Scaling via SpEL):
   ```bash
   docker-compose run --rm load-generator sh -c "apk add --no-cache wrk && echo 'request = function() local uid = math.random(1, 1000000); headers = {}; headers[\"X-User-ID\"] = \"user_\" .. uid; return wrk.format(nil, nil, headers) end' > /tmp/random_user.lua && wrk -t12 -c400 -d30s -s /tmp/random_user.lua http://app-server:8080/api/user-limited"
   ```