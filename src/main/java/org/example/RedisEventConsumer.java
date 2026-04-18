package org.example;

import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
public class RedisEventConsumer implements StreamListener<String, ObjectRecord<String, Event>> {

    static final String GROUP_NAME = "event-processors";
    static final String CONSUMER_NAME = "consumer-1";

    private final StringRedisTemplate redisTemplate;
    private final StreamMessageListenerContainer<String, ObjectRecord<String, Event>> listenerContainer;

    public RedisEventConsumer(
            StringRedisTemplate redisTemplate,
            StreamMessageListenerContainer<String, ObjectRecord<String, Event>> listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
    }

    @PostConstruct
    public void start() {
        // create the consumer group if it doesn't exist
        try {
            redisTemplate.opsForStream().createGroup(RedisEventProducer.STREAM_KEY, GROUP_NAME);
        } catch (Exception e) {
            // group already exists — safe to ignore
        }

        listenerContainer.receive(
                Consumer.from(GROUP_NAME, CONSUMER_NAME),
                StreamOffset.create(RedisEventProducer.STREAM_KEY, ReadOffset.lastConsumed()),
                this
        );

        listenerContainer.start();
    }

    @Override
    public void onMessage(ObjectRecord<String, Event> record) {
        Event event = record.getValue();
        System.out.println("[consumer] processed: " + event);

        redisTemplate.opsForStream().acknowledge(
                RedisEventProducer.STREAM_KEY, GROUP_NAME, record.getId()
        );
    }
}