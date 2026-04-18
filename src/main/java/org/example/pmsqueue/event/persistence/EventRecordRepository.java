package org.example.pmsqueue.event.persistence;

import org.example.pmsqueue.event.domain.EventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 이벤트 원본 로그 저장/조회.
 *
 * <p>시니어 노트: Spring Data JPA의 메서드 이름 파생 쿼리를 사용한다.
 * {@code findByEventId}가 UNIQUE 인덱스를 탄다.
 * TODO(junior): 대용량 이벤트 삽입에서 병목이 생기면 배치 insert/비동기 처리 고려.
 */
public interface EventRecordRepository extends JpaRepository<EventRecordEntity, Long> {

    boolean existsByEventId(String eventId);

    Optional<EventRecordEntity> findByEventId(String eventId);

    long countByEventType(EventType eventType);
}
