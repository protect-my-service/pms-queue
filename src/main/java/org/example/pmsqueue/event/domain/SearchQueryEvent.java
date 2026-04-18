package org.example.pmsqueue.event.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/**
 * 검색 쿼리 이벤트.
 *
 * <p>시나리오: {@link SearchResultClickEvent}와 window join 학습용.
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>{@code queryId}는 쿼리 1회 실행을 식별하는 correlation key.
 *       이어지는 result click 이벤트와 같은 queryId로 매칭되어야 join이 가능.
 *   <li>같은 stream("events:search")에 올라가므로 단일 consumer가 join 가능.
 * </ul>
 */
public record SearchQueryEvent(
        @NotBlank String eventId,
        @NotNull EventType eventType,
        @NotNull Instant occurredAt,
        String userId,
        String sessionId,
        String traceId,
        @NotBlank String queryId,
        @NotBlank String queryText,
        Map<String, String> filters
) implements BaseEvent {
}
