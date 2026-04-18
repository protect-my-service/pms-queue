package org.example.pmsqueue.event.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.example.pmsqueue.event.domain.EventType;

import java.time.Instant;

/**
 * 이벤트 원본 로그 (audit & replay용).
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>{@code eventId}에 UNIQUE 제약을 걸어 "중복 방지 멱등"의 1차 방어선.
 *   <li>{@code payload}는 타입별 JSON 전체를 저장. 스키마 변경이 있어도
 *       원본은 그대로 재현 가능 (replay용).
 *   <li>JSONB/TIMESTAMPTZ 등 DB별 세부 최적화는 베이스 스캐폴드 범위 밖.
 *       팀별 인프라 구축 단계에서 결정.
 * </ul>
 */
@Entity
@Table(
        name = "event_record",
        indexes = {
                @Index(name = "idx_event_record_type_occurred", columnList = "event_type, occurred_at")
        }
)
public class EventRecordEntity {

    public enum Status { RECEIVED, PROCESSED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private EventType eventType;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "CLOB")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    protected EventRecordEntity() {}

    public EventRecordEntity(String eventId, EventType eventType, String payload,
                             Instant occurredAt, Instant receivedAt, Status status) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public EventType getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getReceivedAt() { return receivedAt; }
    public Status getStatus() { return status; }

    public void markProcessed() { this.status = Status.PROCESSED; }
    public void markFailed() { this.status = Status.FAILED; }
}
