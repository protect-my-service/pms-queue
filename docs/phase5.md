# Phase 5: Backpressure Patterns

## What was built

Added rate limiting, circuit breaker, connection timeout, and Retry-After header to protect the service under load and downstream failure.

```
POST /events (normal)         → 202 Accepted
POST /events (rate exceeded)  → 429 Too Many Requests + Retry-After: 1
POST /events (circuit open)   → 503 Service Unavailable
```

## Patterns implemented

| Pattern | Where | What it does |
|---|---|---|
| **Timeout** | `application.properties` | Redis commands fail after 2000ms instead of hanging |
| **Rate limiter** | `EventController` | Max 10 req/s per window. Excess → 429 |
| **Retry-After** | `EventController` fallback | Header on 429 telling client when to retry |
| **Circuit breaker** | `EventController` | Opens after 50% failure rate. Rejects fast → 503 |

Timeout enables the other two — without it, Redis failures take minutes to surface and the circuit breaker never accumulates failures fast enough to act.

## Files modified

- `build.gradle.kts` — added `spring-boot-starter-aop` and `resilience4j-spring-boot3:2.2.0`
- `application.properties` — rate limiter and circuit breaker config, Redis timeouts
- `EventController.java` — `@RateLimiter` + `@CircuitBreaker` annotations with fallbacks

---

## Why these patterns for this project

- **Timeout** — makes failures fast so circuit breaker can react
- **Rate limiting** — protects against a single noisy client flooding the event stream
- **Retry-After** — makes 429 actionable for clients instead of just slamming the door
- **Circuit breaker** — protects against Redis being slow/down causing thread pile-up

---

## Other resilience patterns worth knowing

| Pattern | What it does | When you need it |
|---|---|---|
| **Retry** | Automatically retry with backoff | Transient failures, network blips |
| **Bulkhead** | Isolate thread pools per caller | Prevent one slow client starving others |
| **Fallback** | Return default/cached response on failure | Degraded-but-functional behavior |

---

## What is AOP?

Aspect-Oriented Programming — a way to add cross-cutting behavior (rate limiting, logging, security) around methods without tangling it into each method's logic.

Spring AOP wraps your class in a generated proxy. When you call `receiveEvent()`, the proxy intercepts it, runs the rate limiter and circuit breaker checks, and only calls the real method if they pass. Your method knows nothing about this.

`spring-boot-starter-aop` provides the proxying infrastructure that Resilience4j needs to make its annotations work.

---

## Resilience4j

Standard Java resilience library. Replaced Netflix Hystrix (deprecated 2018). Native Spring Boot 3 integration.

**Alternatives:**
| | Resilience4j | Hystrix | Sentinel |
|---|---|---|---|
| Status | Active, standard | Deprecated | Active (Alibaba) |
| Use when | General Spring apps | Legacy only | Alibaba Cloud stack |

---

## Timeout

```properties
spring.data.redis.timeout=2000ms
spring.data.redis.connect-timeout=2000ms
```

Without these, a hung Redis connection holds the HTTP thread until OS-level TCP timeout (minutes). 2000ms means: fail fast, let the circuit breaker count it as a failure quickly.

This is why **timeout is the most fundamental resilience pattern** — all other patterns depend on failures being detected quickly. A failure that takes 5 minutes to surface is useless for circuit breaking.

---

## Rate limiter

```properties
resilience4j.ratelimiter.instances.events.limit-for-period=10
resilience4j.ratelimiter.instances.events.limit-refresh-period=1s
resilience4j.ratelimiter.instances.events.timeout-duration=0
```

- `limit-for-period=10` — max 10 requests per window
- `limit-refresh-period=1s` — window resets every second (fixed window)
- `timeout-duration=0` — fail immediately if no permit, don't wait

**Fixed window tradeoff:** a client can send 10 at the end of second 1 and 10 at the start of second 2 — 20 requests in a short burst. Sliding window smooths this but is more complex.

**How to choose values:** what can a legitimate client reasonably need? Start conservative, measure real traffic, then loosen.

### Retry-After header

Standard HTTP response header telling the client when it's safe to retry. Set to `"1"` to match `limit-refresh-period=1s`.

```
HTTP/1.1 429
Retry-After: 1
Rate limit exceeded. Retry after 1 second.
```

Without it, clients either give up or retry immediately (making things worse). Well-behaved HTTP clients and SDKs respect this header automatically.

---

## Circuit breaker

```properties
resilience4j.circuitbreaker.instances.events.sliding-window-size=10
resilience4j.circuitbreaker.instances.events.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.events.wait-duration-in-open-state=10s
resilience4j.circuitbreaker.instances.events.permitted-number-of-calls-in-half-open-state=3
```

### States

```
CLOSED → normal, all requests pass through
   ↓ failure rate ≥ 50% over last 10 calls
OPEN → all requests rejected immediately (503)
   ↓ after 10 seconds
HALF-OPEN → 3 test calls allowed through
   ↓ tests succeed        ↓ tests fail
CLOSED                  OPEN again
```

**HALF-OPEN** is what caused the two 202s mixed into the 503s during testing — the circuit let 3 probes through after 10s to check if Redis recovered.

### How to choose values

- `sliding-window-size` — larger = more stable but slower to react. 10 for learning, 20-100 for production.
- `failure-rate-threshold` — 50% is standard. Lower for critical systems (20%). Higher for noisy ones (70%).
- `wait-duration-in-open-state` — how long to give downstream to recover. 10s is short; production typically 30-60s.
- `permitted-number-of-calls-in-half-open-state` — 3-5 is standard, just enough to probe recovery.

---

## Annotation stacking and fallback resolution

```java
@RateLimiter(name = "events", fallbackMethod = "rateLimitFallback")
@CircuitBreaker(name = "events", fallbackMethod = "circuitBreakerFallback")
public ResponseEntity<String> receiveEvent(@RequestBody Event event) { ... }
```

Resilience4j AOP execution order (outer → inner):
```
RateLimiter → CircuitBreaker → actual method
```

`@RateLimiter` has higher AOP aspect priority in Resilience4j, making it the outermost decorator — even though both annotations appear on the same method. The annotation written first in code happens to match the outer execution order here, but this is determined by Resilience4j's aspect ordering, not annotation declaration order.

Call flow when Redis is down and circuit is CLOSED:
```
RateLimiter (passes) → CircuitBreaker (passes) → publish() throws QueryTimeoutException
→ propagates to CircuitBreaker → circuitBreakerFallback → 503
→ circuit breaker counts as FAILURE
```

Call flow when circuit is OPEN:
```
RateLimiter (passes) → CircuitBreaker rejects → circuitBreakerFallback → 503 (fast)
```

Call flow when rate limited:
```
RateLimiter rejects → rateLimitFallback → 429 + Retry-After
```

---

## Bug found and fixed during testing

### The bug

Initial fallback signature used `Exception`:
```java
public ResponseEntity<String> rateLimitFallback(Event event, Exception e)
```

Resilience4j routes **any unhandled exception** to the fallback method — not just `RequestNotPermitted`. Since `@RateLimiter` is the outermost aspect, when `publish()` threw `QueryTimeoutException` (Redis down), it propagated all the way out and the rate limiter fallback caught it, returning 429. The circuit breaker (inner) never got to handle it and counted it as a **SUCCESS** — it never accumulated failures, never opened.

### How it was identified

Added `System.out.println` to both fallback methods and `logging.level.io.github.resilience4j=DEBUG`. The logs revealed:
```
[fallback] rateLimitFallback called. Exception: org.springframework.dao.QueryTimeoutException
CircuitBreaker 'events' recorded a successful call. Elapsed time: 2009 ms
```

Two signals:
1. Wrong fallback being called (rate limiter, not circuit breaker)
2. Circuit breaker recording SUCCESS despite Redis being down

The exception class name (`QueryTimeoutException` not `RequestNotPermitted`) exposed the mismatch immediately.

### The fix

Use the specific exception type in the rate limiter fallback:
```java
public ResponseEntity<String> rateLimitFallback(Event event, RequestNotPermitted e)
```

`RequestNotPermitted` is the specific exception Resilience4j throws when rate limit is exceeded. Using it means this fallback is ONLY called for rate limit events — Redis timeouts now propagate inward to the circuit breaker as intended.

### Debugging toolkit for Resilience4j

1. **Add println to fallback methods** — immediately shows which fallback is called and with what exception
2. **`logging.level.io.github.resilience4j=DEBUG`** — logs every state transition, permission granted/denied, failure recorded
3. **Log `e.getClass().getName()`** — always log the class name, not just the message. The class reveals whether the exception is what you expected.
4. **Spring Boot Actuator** (not used here, but exists) — exposes live circuit breaker state via `/actuator/circuitbreakers`

---

## What is Lettuce?

Lettuce is the Redis client library Spring Data Redis uses internally — manages TCP connections, speaks the Redis wire protocol, handles reconnection.

```
Your code (StringRedisTemplate)
    ↓
Spring Data Redis
    ↓
Lettuce (TCP connections, Redis protocol)
    ↓
Redis server
```

You never use Lettuce directly. It appears in logs as `io.lettuce`:
```
i.l.core.protocol.ConnectionWatchdog: Reconnecting...
i.l.core.protocol.ReconnectionHandler: Reconnected to localhost:6379
```

Lettuce reconnects automatically when Redis comes back — independent of the circuit breaker. The two work together: Lettuce handles the TCP layer, circuit breaker controls whether your app code even attempts to use it.

**Lettuce vs Jedis:** Jedis is the older alternative. Lettuce is the Spring Boot default — non-blocking, better connection pool management.

---

## Gotchas

- **`@RateLimiter` fallback catches ALL exceptions** — always use `RequestNotPermitted` in the fallback signature, not `Exception`. Otherwise Redis/DB failures silently return 429 instead of reaching the circuit breaker.
- **Circuit breaker counts fallback return as SUCCESS** — if the outer fallback swallows an exception and returns normally, the inner circuit breaker never opens.
- **Timeout too short causes app startup failure** — initially tried `timeout=500ms`. The `@PostConstruct` in `RedisEventConsumer` runs `createGroup` and starts the listener container on first connect, which can exceed 500ms. Set to `2000ms`. In production, consider separate startup vs runtime timeout configs.
- **Half-open produces mixed 202/503** — expected, not a bug. The circuit is probing recovery.
- **Resilience4j is not a Spring Boot starter** — must specify version explicitly in `build.gradle.kts`.
- **IntelliJ warns fallback methods are "unused"** — they're called via reflection by Resilience4j, safe to ignore.