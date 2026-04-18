package org.example.pmsqueue.event.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * API 오류 이벤트 — 격리된 스트림으로 송신.
 *
 * <p>시나리오: 에러 폭증 시 다른 이벤트 처리가 지연되지 않도록 격리(bulkhead).
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>분석 이벤트와 운영 에러는 SLA가 다르다. 같은 스트림에 섞이면
 *       에러 폭증 시 analytics 처리도 지연된다. → 별도 stream/consumer group.
 *   <li>방어팀은 이 컨슈머 앞단에 drop/rate-limit을 붙여 공격팀의 에러 폭증을 막는다.
 * </ul>
 */
public record ApiErrorEvent(
        @NotBlank String eventId,
        @NotNull EventType eventType,
        @NotNull Instant occurredAt,
        String userId,
        String sessionId,
        String traceId,
        @NotBlank String service,
        @NotBlank String endpoint,
        Integer httpStatus,
        String errorCode,
        String message
) implements BaseEvent {
}
