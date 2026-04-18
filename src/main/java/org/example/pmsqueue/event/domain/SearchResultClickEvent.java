package org.example.pmsqueue.event.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 검색 결과 클릭 이벤트 — {@link SearchQueryEvent}와 window join 대상.
 *
 * <p>시니어 노트: {@code queryId}는 연관된 search_query 이벤트의 queryId와 일치해야 함.
 * "같은 쿼리에서 나온 결과를 몇 번째 순위에서 클릭했는가"를 분석하려면 join이 필수.
 */
public record SearchResultClickEvent(
        @NotBlank String eventId,
        @NotNull EventType eventType,
        @NotNull Instant occurredAt,
        String userId,
        String sessionId,
        String traceId,
        @NotBlank String queryId,
        @NotBlank String resultId,
        @Min(1) Integer rank
) implements BaseEvent {
}
