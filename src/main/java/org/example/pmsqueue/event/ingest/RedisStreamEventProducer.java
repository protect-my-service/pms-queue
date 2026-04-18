package org.example.pmsqueue.event.ingest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.pmsqueue.common.exception.DownstreamUnavailableException;
import org.example.pmsqueue.event.domain.BaseEvent;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Redis Stream으로 이벤트 적재.
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>타입별 stream key 라우팅은 {@code event.eventType().streamKey()}로 수행.
 *       새 타입 추가 시 EventType enum만 수정하면 자동으로 새 stream이 생성된다.
 *   <li>{@code XADD} 호출이 실패하면 {@link DownstreamUnavailableException}으로 변환해
 *       ProblemDetail 계약에 맞춘다. 이 예외가 발생하면 클라이언트는 재시도해야 한다.
 *   <li>TODO(junior): {@code MAXLEN} 옵션을 추가해 stream 무제한 증가 방지.
 *       예: {@code XADD events:click MAXLEN ~ 500000 *} — approximate trim.
 * </ul>
 */
@Component
public class RedisStreamEventProducer implements EventProducer {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisStreamEventProducer(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(BaseEvent event) {
        String streamKey = event.eventType().streamKey();
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Event serialization failed: " + event.eventType(), e);
        }

        MapRecord<String, String, String> record = StreamRecords.newRecord()
                .ofMap(Map.of(
                        "eventId", event.eventId(),
                        "eventType", event.eventType().name(),
                        "payload", payload
                ))
                .withStreamKey(streamKey);

        try {
            redisTemplate.opsForStream().add(record);
        } catch (RuntimeException e) {
            throw new DownstreamUnavailableException("redis-stream", e)
                    .with("streamKey", streamKey)
                    .with("eventId", event.eventId());
        }
    }
}
