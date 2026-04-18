package org.example.pmsqueue.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.pmsqueue.event.domain.ClickEvent;
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
 * click 컨슈머 — 샘플링 적용 시나리오.
 *
 * <p>시니어 노트: 샘플링은 볼륨을 N분의 1로 줄이는 동시에 대표성을 잃지 않는 기법.
 * 세 가지 전략과 트레이드오프:
 *
 * <ul>
 *   <li><b>해시 기반 결정적 샘플링</b> — {@code hash(userId) % N == 0}. 같은 사용자가 항상 샘플에
 *       포함되므로 퍼널 분석에 유리. 사용자 분포가 편향되면 대표성이 떨어진다.
 *   <li><b>확률 기반 샘플링</b> — {@code random() < 1/N}. 간단하지만 같은 사용자라도 매번 선별이 달라
 *       퍼널 분석에 불리. 집계 통계에는 충분.
 *   <li><b>Reservoir 샘플링</b> — 고정 크기 버퍼에 uniform 하게 유지. 메모리·코드 복잡도 증가.
 *       시간 윈도우당 상위 N건 유지 같은 특수 목적에 사용.
 * </ul>
 *
 * <p>TODO(junior):
 * <ul>
 *   <li>샘플링 비율을 application.properties로 외부화 (방어팀이 런타임 튜닝).
 *   <li>드롭된 이벤트 수를 metric으로 노출 (Micrometer counter).
 *   <li>공격팀의 click 폭증 시, 샘플링으로 근사 유지하되 DB 병목이 발생하지 않도록 보장.
 * </ul>
 */
@Component
public class ClickConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(ClickConsumer.class);
    private static final String GROUP = "click-processors";
    private static final String CONSUMER = "consumer-1";

    // TODO(junior): 샘플링 비율 외부화. 지금은 10개 중 1개만 저장 (1/10 = 10%).
    private static final int SAMPLE_ONE_IN_N = 10;

    private final StringRedisTemplate redisTemplate;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final EventRecordRepository repository;
    private final ObjectMapper objectMapper;

    public ClickConsumer(StringRedisTemplate redisTemplate,
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
        String streamKey = EventType.CLICK.streamKey();
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
        String streamKey = EventType.CLICK.streamKey();
        try {
            String eventId = record.getValue().get("eventId");
            String payload = record.getValue().get("payload");
            ClickEvent event = objectMapper.readValue(payload, ClickEvent.class);

            // 결정적 해시 샘플링 — 같은 userId는 항상 동일 샘플 결정.
            // TODO(junior): 해시 vs 확률 vs reservoir 선택 + 이유 주석으로 기록.
            if (Math.floorMod(Integer.hashCode(eventId.hashCode()), SAMPLE_ONE_IN_N) == 0) {
                if (!repository.existsByEventId(eventId)) {
                    repository.save(new EventRecordEntity(
                            eventId, EventType.CLICK, payload,
                            event.occurredAt(), Instant.now(), EventRecordEntity.Status.PROCESSED
                    ));
                }
            } else {
                log.debug("click sampled out: {}", eventId);
            }

            // 샘플링 여부와 무관하게 ACK — click은 best-effort.
            redisTemplate.opsForStream().acknowledge(streamKey, GROUP, record.getId());
        } catch (Exception e) {
            log.warn("click processing error (drop): {}", e.getMessage());
            // best-effort — ACK해서 재처리 방지.
            redisTemplate.opsForStream().acknowledge(streamKey, GROUP, record.getId());
        }
    }
}
