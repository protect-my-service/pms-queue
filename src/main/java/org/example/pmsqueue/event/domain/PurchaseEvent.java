package org.example.pmsqueue.event.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 구매 완료 이벤트.
 *
 * <p>시나리오: 볼륨 적음, 손실 불가, 중복 방지(UNIQUE 제약 기반 멱등).
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>{@code eventId}가 곧 멱등 키. 동일 eventId의 두 번째 처리는 DB UNIQUE 위반으로 거절된다.
 *   <li>"실제 exactly-once"는 단일 인스턴스 + UNIQUE 제약으로 근사한 것이며,
 *       분산 환경에서의 정확한 exactly-once는 outbox/two-phase 등 별도 작업임.
 *       과장 표현 지양 — 이 구현은 "중복 방지(멱등)"이라 부른다.
 *   <li>{@code orderId}는 비즈니스 도메인의 주문 식별자. eventId와는 별개.
 *       같은 주문에 대해 PENDING → PAID → REFUNDED 등 여러 이벤트가 생성될 수 있음.
 * </ul>
 */
public record PurchaseEvent(
        @NotBlank String eventId,
        @NotNull EventType eventType,
        @NotNull Instant occurredAt,
        String userId,
        String sessionId,
        String traceId,
        @NotBlank String orderId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String currency
) implements BaseEvent {
}
