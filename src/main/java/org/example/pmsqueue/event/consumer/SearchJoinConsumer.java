package org.example.pmsqueue.event.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.example.pmsqueue.event.domain.EventType;
import org.example.pmsqueue.event.domain.SearchQueryEvent;
import org.example.pmsqueue.event.domain.SearchResultClickEvent;
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

/**
 * search_query + search_result_click의 윈도우 조인 학습용 컨슈머.
 *
 * <p>시니어 노트 — 스트림 조인 개념:
 * <ul>
 *   <li><b>Watermark</b>: "더 이상 이 시점 이전의 이벤트는 오지 않는다"라고 선언하는 시각.
 *       watermark 이전 이벤트는 버퍼에서 쏟아내도 안전.
 *   <li><b>Late arrival</b>: watermark 이후에 도착한 과거 이벤트. 정책 선택:
 *       (1) drop, (2) 별도 late-events 스트림으로 분리, (3) 결과 이벤트를 정정(upsert).
 *   <li><b>Out-of-order</b>: 같은 queryId의 query/result_click 순서가 뒤집혀 도착 가능.
 *       버퍼에 두고 "둘 다 모일 때까지" 대기 + 타임아웃 시 반쪽만 있으면 drop/emit 결정.
 * </ul>
 *
 * <p>TODO(junior):
 * <ul>
 *   <li>Redis Hash로 queryId 별 이벤트 쌍을 5분 TTL 버퍼로 두고, 양쪽이 모이면 조인 결과를
 *       파생 이벤트로 발행(예: events:search_session). 단일 쪽만 TTL 만료되면 drop 또는 별도 기록.
 *   <li>같은 queryId에서 여러 result_click이 올 수 있음 — 1:N 조인 전략 결정.
 *   <li>메모리 사용량·지연·정확도 트레이드오프 문서화.
 * </ul>
 */
@Component
public class SearchJoinConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger log = LoggerFactory.getLogger(SearchJoinConsumer.class);
    private static final String GROUP = "search-join-processors";
    private static final String CONSUMER = "consumer-1";

    private final StringRedisTemplate redisTemplate;
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final ObjectMapper objectMapper;

    public SearchJoinConsumer(StringRedisTemplate redisTemplate,
                              StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.container = container;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        String streamKey = EventType.SEARCH_QUERY.streamKey(); // "events:search"
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
        String streamKey = EventType.SEARCH_QUERY.streamKey();
        try {
            String payload = record.getValue().get("payload");
            String eventType = record.getValue().get("eventType");

            switch (EventType.valueOf(eventType)) {
                case SEARCH_QUERY -> {
                    SearchQueryEvent q = objectMapper.readValue(payload, SearchQueryEvent.class);
                    // TODO(junior): Redis Hash에 queryId → query 저장 (TTL 5분). result_click 도착 시 join.
                    log.info("search_query received: queryId={}", q.queryId());
                }
                case SEARCH_RESULT_CLICK -> {
                    SearchResultClickEvent rc = objectMapper.readValue(payload, SearchResultClickEvent.class);
                    // TODO(junior): Redis Hash에서 queryId lookup. 있으면 join 후 파생 이벤트 발행.
                    //   없으면 late arrival 정책(별도 스트림 기록 등).
                    log.info("search_result_click received: queryId={}, rank={}", rc.queryId(), rc.rank());
                }
                default -> log.warn("unexpected event on search stream: {}", eventType);
            }

            redisTemplate.opsForStream().acknowledge(streamKey, GROUP, record.getId());
        } catch (Exception e) {
            log.warn("search-join processing error: {}", e.getMessage());
        }
    }
}
