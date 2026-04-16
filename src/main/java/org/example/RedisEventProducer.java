package org.example;

import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisEventProducer {

    static final String STREAM_KEY = "events";

    private final StringRedisTemplate redisTemplate;

    public RedisEventProducer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publish(Event event) {
        ObjectRecord<String, Event> record = StreamRecords
                .newRecord()
                .ofObject(event)
                .withStreamKey(STREAM_KEY);

        redisTemplate.opsForStream().add(record);
    }
}