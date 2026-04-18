package org.example.pmsqueue.event.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 페이지 방문 이벤트.
 *
 * <p>시나리오: 볼륨이 많고, 손실 허용, at-least-once.
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>가장 단순한 분석 이벤트. 컨슈머 구현의 기준선으로 사용한다.
 *   <li>볼륨이 크므로 DB insert가 곧 병목이다. 배치 insert/비동기 write가 주니어의 첫 성능 개선 과제.
 * </ul>
 */
public record PageViewEvent(
        @NotBlank String eventId,
        @NotNull EventType eventType,
        @NotNull Instant occurredAt,
        String userId,
        String sessionId,
        String traceId,
        @NotBlank String url,
        String referrer,
        String userAgent
) implements BaseEvent {
}
