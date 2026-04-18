package org.example.pmsqueue.event.api;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.example.pmsqueue.common.exception.DownstreamUnavailableException;
import org.example.pmsqueue.common.exception.RateLimitedException;
import org.example.pmsqueue.event.domain.ApiErrorEvent;
import org.example.pmsqueue.event.domain.ClickEvent;
import org.example.pmsqueue.event.domain.PageViewEvent;
import org.example.pmsqueue.event.domain.PurchaseEvent;
import org.example.pmsqueue.event.domain.SearchQueryEvent;
import org.example.pmsqueue.event.domain.SearchResultClickEvent;
import org.example.pmsqueue.event.ingest.EventProducer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이벤트 수신 HTTP 엔드포인트.
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>타입별 핸들러 메서드로 분리했다. Jackson polymorphism 없이 각 메서드가
 *       구체 record에 직접 바인딩하므로 주니어가 요청/응답 흐름을 한 파일에서 읽고 이해 가능.
 *   <li>{@code @RateLimiter}/{@code @CircuitBreaker}는 타입별로 분리된 이름을 사용해
 *       방어팀이 타입별 임계치를 독립 튜닝할 수 있다 (성능 개선 과제).
 *   <li>검증 실패는 {@code @Valid} → {@code MethodArgumentNotValidException} →
 *       {@link org.example.pmsqueue.common.exception.GlobalExceptionHandler} 에서 422 ProblemDetail로 변환됨.
 *   <li>TODO(junior): 비동기 응답(deferred result) 적용 여부 검토.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/events")
public class EventIngestController {

    private final EventProducer producer;

    public EventIngestController(EventProducer producer) {
        this.producer = producer;
    }

    @PostMapping("/page_view")
    @RateLimiter(name = "events-page_view", fallbackMethod = "pageViewRateFallback")
    @CircuitBreaker(name = "events-page_view", fallbackMethod = "pageViewCircuitFallback")
    public ResponseEntity<Void> pageView(@RequestBody @Valid PageViewEvent event) {
        producer.send(event);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/click")
    @RateLimiter(name = "events-click", fallbackMethod = "clickRateFallback")
    @CircuitBreaker(name = "events-click", fallbackMethod = "clickCircuitFallback")
    public ResponseEntity<Void> click(@RequestBody @Valid ClickEvent event) {
        producer.send(event);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/search_query")
    @RateLimiter(name = "events-search", fallbackMethod = "searchQueryRateFallback")
    @CircuitBreaker(name = "events-search", fallbackMethod = "searchQueryCircuitFallback")
    public ResponseEntity<Void> searchQuery(@RequestBody @Valid SearchQueryEvent event) {
        producer.send(event);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/search_result_click")
    @RateLimiter(name = "events-search", fallbackMethod = "searchResultClickRateFallback")
    @CircuitBreaker(name = "events-search", fallbackMethod = "searchResultClickCircuitFallback")
    public ResponseEntity<Void> searchResultClick(@RequestBody @Valid SearchResultClickEvent event) {
        producer.send(event);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/purchase")
    @RateLimiter(name = "events-purchase", fallbackMethod = "purchaseRateFallback")
    @CircuitBreaker(name = "events-purchase", fallbackMethod = "purchaseCircuitFallback")
    public ResponseEntity<Void> purchase(@RequestBody @Valid PurchaseEvent event) {
        producer.send(event);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api_error")
    @RateLimiter(name = "events-api_error", fallbackMethod = "apiErrorRateFallback")
    @CircuitBreaker(name = "events-api_error", fallbackMethod = "apiErrorCircuitFallback")
    public ResponseEntity<Void> apiError(@RequestBody @Valid ApiErrorEvent event) {
        producer.send(event);
        return ResponseEntity.accepted().build();
    }

    // ----- Fallbacks -----

    public ResponseEntity<Void> pageViewRateFallback(PageViewEvent event, RequestNotPermitted e) {
        throw new RateLimitedException("page_view");
    }
    public ResponseEntity<Void> pageViewCircuitFallback(PageViewEvent event, Exception e) {
        throw new DownstreamUnavailableException("redis-stream", e);
    }

    public ResponseEntity<Void> clickRateFallback(ClickEvent event, RequestNotPermitted e) {
        throw new RateLimitedException("click");
    }
    public ResponseEntity<Void> clickCircuitFallback(ClickEvent event, Exception e) {
        throw new DownstreamUnavailableException("redis-stream", e);
    }

    public ResponseEntity<Void> searchQueryRateFallback(SearchQueryEvent event, RequestNotPermitted e) {
        throw new RateLimitedException("search_query");
    }
    public ResponseEntity<Void> searchQueryCircuitFallback(SearchQueryEvent event, Exception e) {
        throw new DownstreamUnavailableException("redis-stream", e);
    }

    public ResponseEntity<Void> searchResultClickRateFallback(SearchResultClickEvent event, RequestNotPermitted e) {
        throw new RateLimitedException("search_result_click");
    }
    public ResponseEntity<Void> searchResultClickCircuitFallback(SearchResultClickEvent event, Exception e) {
        throw new DownstreamUnavailableException("redis-stream", e);
    }

    public ResponseEntity<Void> purchaseRateFallback(PurchaseEvent event, RequestNotPermitted e) {
        throw new RateLimitedException("purchase");
    }
    public ResponseEntity<Void> purchaseCircuitFallback(PurchaseEvent event, Exception e) {
        throw new DownstreamUnavailableException("redis-stream", e);
    }

    public ResponseEntity<Void> apiErrorRateFallback(ApiErrorEvent event, RequestNotPermitted e) {
        throw new RateLimitedException("api_error");
    }
    public ResponseEntity<Void> apiErrorCircuitFallback(ApiErrorEvent event, Exception e) {
        throw new DownstreamUnavailableException("redis-stream", e);
    }
}
