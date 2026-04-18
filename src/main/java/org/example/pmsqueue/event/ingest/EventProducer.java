package org.example.pmsqueue.event.ingest;

import org.example.pmsqueue.event.domain.BaseEvent;

/**
 * 이벤트 송신 포트(port). 구현체가 Redis Stream으로 라우팅.
 *
 * <p>시니어 노트: interface로 분리한 이유는 학습 목적상 "저장소 스위치" 실험을
 * 쉽게 하기 위함이다. 예: Kafka로 바꿔보기, in-memory로 바꿔보기.
 * 다만 포스트 스캐폴드 단계에서 신규 구현체 추가는 권장하지 않는다 (ADR-0002).
 */
public interface EventProducer {

    void send(BaseEvent event);
}
