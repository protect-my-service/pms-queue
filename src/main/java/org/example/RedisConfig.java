package org.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.hash.ObjectHashMapper;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    public ObjectHashMapper objectHashMapper() {
        return new ObjectHashMapper();
    }

    @Bean
    public StreamMessageListenerContainer<String, ObjectRecord<String, Event>> streamListenerContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainerOptions<String, ObjectRecord<String, Event>> options =
                StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .targetType(Event.class)
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }
}