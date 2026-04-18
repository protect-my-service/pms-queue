package org.example;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/events")
public class EventController {

    private final RedisEventProducer producer;
    private final StringRedisTemplate redisTemplate;

    public EventController(RedisEventProducer producer, StringRedisTemplate redisTemplate) {
        this.producer = producer;
        this.redisTemplate = redisTemplate;
    }

    @PostMapping
    @RateLimiter(name = "events", fallbackMethod = "rateLimitFallback")
    @CircuitBreaker(name = "events", fallbackMethod = "circuitBreakerFallback")
    public ResponseEntity<String> receiveEvent(@RequestBody Event event) {
        event.setReceivedAt(Instant.now());
        producer.publish(event);
        return ResponseEntity.accepted().body("Accepted");
    }

    public ResponseEntity<String> rateLimitFallback(Event event, RequestNotPermitted e) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "1");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body("Rate limit exceeded. Retry after 1 second.");
    }

    public ResponseEntity<String> circuitBreakerFallback(Event event, Exception e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Service temporarily unavailable. Please try again later.");
    }

    // Redis CLI equivalent: XRANGE events - +
    @GetMapping
    public List<Map<Object, Object>> getEvents() {
        List<MapRecord<String, Object, Object>> records =
                redisTemplate.opsForStream().range(RedisEventProducer.STREAM_KEY, Range.unbounded());
        if (records == null) return List.of();
        return records.stream()
                .map(MapRecord::getValue)
                .collect(Collectors.toList());
    }
}