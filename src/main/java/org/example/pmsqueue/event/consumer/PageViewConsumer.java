package org.example.pmsqueue.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.pmsqueue.event.domain.EventType;
import org.example.pmsqueue.event.domain.PageViewEvent;
import org.example.pmsqueue.event.persistence.EventRecordEntity;
import org.example.pmsqueue.event.persistence.EventRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * page_view 컨슈머 — at-least-once, 손실 허용.
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>이 컨슈머는 "기준선"이다. 다른 컨슈머 구현의 출발점으로 삼는다.
 *   <li>JPA insert + ACK 의 단순 흐름. 실패 시 ACK하지 않으면 XPENDING으로 남아 재처리 대상이 된다.
 *   <li>TODO(junior):
 *       <ul>
 *         <li>처리 실패(예: DB 장애) 시 재시도 횟수를 제한하고, 초과 시 DLQ로 이동하거나 drop하라.
 *         <li>손실 허용 시나리오이므로 실패 누적 시 alerting 없이 drop하는 것도 합리적 선택.
 *         <li>Consumer name을 환경변수에서 받아 multi-instance 수평 확장 시 충돌 방지.
 *       </ul>
 * </ul>
 */
@Component
public class PageViewConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(PageViewConsumer.class);
    private static final String GROUP = "page-view-processors";
    private static final String CONSUMER = "consumer-1";

    private final StringRedisTemplate redisTemplate;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final EventRecordRepository repository;
    private final ObjectMapper objectMapper;

    public PageViewConsumer(StringRedisTemplate redisTemplate,
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
        String streamKey = EventType.PAGE_VIEW.streamKey();
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
        String streamKey = EventType.PAGE_VIEW.streamKey();
        try {
            String eventId = record.getValue().get("eventId");
            String payload = record.getValue().get("payload");
            PageViewEvent event = objectMapper.readValue(payload, PageViewEvent.class);

            if (!repository.existsByEventId(eventId)) {
                repository.save(new EventRecordEntity(
                        eventId, EventType.PAGE_VIEW, payload,
                        event.occurredAt(), Instant.now(), EventRecordEntity.Status.PROCESSED
                ));
            }
            redisTemplate.opsForStream().acknowledge(streamKey, GROUP, record.getId());
        } catch (Exception e) {
            // TODO(junior): retry 횟수 체크 후 DLQ 또는 drop.
            log.warn("page_view processing failed, will retry via XPENDING: {}", e.getMessage());
        }
    }
}
