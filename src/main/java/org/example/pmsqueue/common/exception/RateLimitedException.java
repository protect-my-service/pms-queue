package org.example.pmsqueue.common.exception;

/**
 * Rate limit 초과 (429).
 *
 * <p>시니어 노트: Resilience4j의 {@code RequestNotPermitted}를 컨트롤러 fallback에서
 * 이 예외로 매핑해 던진다. 이렇게 하면 모든 에러 응답이 ProblemDetail 계약 하나로 통일된다.
 */
public class RateLimitedException extends BusinessException {

    public RateLimitedException(String eventType) {
        super(ErrorCode.RATE_LIMITED, "Rate limit exceeded for event type: " + eventType);
        with("eventType", eventType);
    }
}
