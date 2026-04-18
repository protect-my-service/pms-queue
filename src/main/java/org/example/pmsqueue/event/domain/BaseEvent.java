package org.example.pmsqueue.event.domain;

import java.time.Instant;

/**
 * 모든 이벤트의 공통 계약.
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>sealed 가 아닌 일반 interface로 둔 이유: 학습 목적상 "Jackson polymorphism
 *       없이 타입별 핸들러가 각 concrete type에 직접 바인딩"하는 단순 구조를 유지하기 위함.
 *   <li>{@code eventId}는 클라이언트가 UUID로 생성해서 보내야 한다. 서버가 생성하면
 *       재전송(retry) 시 같은 이벤트가 서로 다른 id로 두 번 저장된다. → 멱등 깨짐.
 *   <li>{@code traceId}는 공격/방어 시나리오에서 요청 흐름을 상관관계로 묶는 핵심.
 *       TODO(junior): MDC에 traceId를 넣어 로그에 자동 기록되도록.
 * </ul>
 */
public interface BaseEvent {

    String eventId();

    EventType eventType();

    Instant occurredAt();

    String userId();

    String sessionId();

    String traceId();
}
