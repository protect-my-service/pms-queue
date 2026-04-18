package org.example.pmsqueue.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.pmsqueue.event.domain.ApiErrorEvent;
import org.example.pmsqueue.event.domain.EventType;
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
 * api_error 컨슈머 — 격리된 스트림.
 *
 * <p>시니어 노트 — 왜 격리된 스트림인가:
 * <ul>
 *   <li>분석 이벤트(page_view/click 등)와 운영 에러 이벤트는 SLA가 다르다.
 *       분석은 지연 10초도 허용, 운영 에러는 즉시 알림이 필요.
 *   <li>같은 스트림에 섞이면 에러 폭증이 분석 파이프라인까지 지연시킨다(HOL blocking).
 *   <li>별도 스트림 + 별도 consumer group = 격벽(bulkhead). 이것이 시나리오의 핵심.
 * </ul>
 *
 * <p>TODO(junior):
 * <ul>
 *   <li>에러 폭증 감지: sliding-window로 초당 에러 수가 임계치 초과 시 drop/샘플링 전환.
 *   <li>에러 분류: httpStatus 5xx vs 4xx, service별 분기 → 알림 채널 분기.
 *   <li>방어 레이어: 공격팀이 이 엔드포인트를 폭격해도 다른 이벤트 처리가 영향받지 않음을 시연/측정.
 * </ul>
 */
@Component
public class ApiErrorConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorConsumer.class);
    private static final String GROUP = "api-error-processors";
    private static final String CONSUMER = "consumer-1";

    private final StringRedisTemplate redisTemplate;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final EventRecordRepository repository;
    private final ObjectMapper objectMapper;

    public ApiErrorConsumer(StringRedisTemplate redisTemplate,
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
        String streamKey = EventType.API_ERROR.streamKey();
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
        String streamKey = EventType.API_ERROR.streamKey();
        try {
            String eventId = record.getValue().get("eventId");
            String payload = record.getValue().get("payload");
            ApiErrorEvent event = objectMapper.readValue(payload, ApiErrorEvent.class);

            if (!repository.existsByEventId(eventId)) {
                repository.save(new EventRecordEntity(
                        eventId, EventType.API_ERROR, payload,
                        event.occurredAt(), Instant.now(), EventRecordEntity.Status.PROCESSED
                ));
            }
            // TODO(junior): 에러 분류 + 알림 분기 + 폭증 감지.

            redisTemplate.opsForStream().acknowledge(streamKey, GROUP, record.getId());
        } catch (Exception e) {
            log.warn("api_error processing failed: {}", e.getMessage());
        }
    }
}
