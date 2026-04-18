package org.example.pmsqueue.event.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * 요소 클릭 이벤트.
 *
 * <p>시나리오: 볼륨이 매우 많음, 샘플링 대상.
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>모든 클릭을 저장하면 DB/스트림이 순식간에 포화된다.
 *       "1/N 샘플링"을 통해 근사 통계를 유지하며 저장 부하를 낮춘다.
 *   <li>샘플링 전략 선택은 {@code ClickConsumer}의 TODO 블록 참조.
 * </ul>
 */
public record ClickEvent(
        @NotBlank String eventId,
        @NotNull EventType eventType,
        @NotNull Instant occurredAt,
        String userId,
        String sessionId,
        String traceId,
        @NotBlank String elementId,
        @NotBlank String page,
        Integer positionX,
        Integer positionY
) implements BaseEvent {
}
