package org.example.pmsqueue.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.pmsqueue.common.exception.DuplicateEventException;
import org.example.pmsqueue.event.domain.EventType;
import org.example.pmsqueue.event.domain.PurchaseEvent;
import org.example.pmsqueue.event.persistence.EventRecordEntity;
import org.example.pmsqueue.event.persistence.EventRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * purchase 컨슈머 — 중복 방지(UNIQUE 기반 멱등).
 *
 * <p>시니어 노트 — "exactly-once" 표기에 대한 경고:
 * <ul>
 *   <li>분산 환경의 진짜 exactly-once는 거의 불가능하다. 메시지 큐는 대부분 at-least-once.
 *       이 구현은 <b>"중복 방지 멱등"</b>이다. DB UNIQUE 제약으로 같은 eventId의 두 번째 insert를 거절.
 *   <li>ACK 타이밍 딜레마:
 *       <ul>
 *         <li>DB commit 먼저 → ACK 전에 장애 → 재처리 시 UNIQUE 위반(멱등) → 안전.
 *         <li>ACK 먼저 → DB commit 전 장애 → 이벤트 유실 (purchase에서는 금물).
 *       </ul>
 *       따라서 <b>DB commit 먼저, 그 후 ACK</b>. 이 순서가 무너지는 리팩토링은 금지.
 *   <li>TODO(junior):
 *       <ul>
 *         <li>ACK 실패 시 재시도 정책. 재시도 무한 루프 방지.
 *         <li>Outbox 패턴으로 DB 트랜잭션과 "다음 큐 publish"를 원자화하는 확장 실습.
 *         <li>결제 재처리가 허용되지 않는다면 DuplicateEventException을 잡아 ACK만 수행.
 *       </ul>
 * </ul>
 */
@Component
public class PurchaseConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(PurchaseConsumer.class);
    private static final String GROUP = "purchase-processors";
    private static final String CONSUMER = "consumer-1";

    private final StringRedisTemplate redisTemplate;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final EventRecordRepository repository;
    private final ObjectMapper objectMapper;

    public PurchaseConsumer(StringRedisTemplate redisTemplate,
                            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
                            EventRecordRepository repository,
                            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.container = container;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        String streamKey = EventType.PURCHASE.streamKey();
        try {
            redisTemplate.opsForStream().createGroup(streamKey, GROUP);
        } catch (Exception ignored) {
            // group already exists
        }
        container.receive(
                Consumer.from(GROUP, CONSUMER),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                this
        );
    }

    @Override
    public void onMessage(MapRecord<String, String, String> record) {
        String streamKey = EventType.PURCHASE.streamKey();
        String eventId = record.getValue().get("eventId");
        try {
            process(record);
            redisTemplate.opsForStream().acknowledge(streamKey, GROUP, record.getId());
        } catch (DuplicateEventException e) {
            // 이미 처리된 이벤트 — 멱등 성공으로 간주하고 ACK.
            log.info("purchase duplicate, already processed: {}", eventId);
            redisTemplate.opsForStream().acknowledge(streamKey, GROUP, record.getId());
        } catch (Exception e) {
            // ACK 하지 않음 → XPENDING에 남아 재처리 대상.
            log.warn("purchase processing failed, will retry via XPENDING: eventId={}, cause={}",
                    eventId, e.getMessage());
        }
    }

    @Transactional
    protected void process(MapRecord<String, String, String> record) {
        String eventId = record.getValue().get("eventId");
        String payload = record.getValue().get("payload");
        try {
            PurchaseEvent event = objectMapper.readValue(payload, PurchaseEvent.class);
            repository.save(new EventRecordEntity(
                    eventId, EventType.PURCHASE, payload,
                    event.occurredAt(), Instant.now(), EventRecordEntity.Status.PROCESSED
            ));
            // TODO(junior): 여기에 결제 완료 후속 작업 (예: 이메일 발행 이벤트 produce) 추가.
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 위반 → 중복 처리.
            throw new DuplicateEventException(eventId, e);
        } catch (DuplicateEventException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("purchase payload deserialize failed: " + eventId, e);
        }
    }
}
