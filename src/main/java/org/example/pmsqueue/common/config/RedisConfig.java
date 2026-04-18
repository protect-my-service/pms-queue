package org.example.pmsqueue.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer.StreamMessageListenerContainerOptions;

import java.time.Duration;

/**
 * Redis Stream 설정.
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>타입별 listener container를 5개 분리하지 않고 단일 container를 사용한다.
 *       학습용 스캐폴드에서 과도한 분할은 가독성을 해친다. 성능 개선 과제에서
 *       bulkhead 수준을 올릴 때 분할을 고려.
 *   <li>{@code MapRecord<String, String, String>} 기반. 각 이벤트는
 *       {@code {"payload": "<json>", "eventType": "PURCHASE"}} 형태로 직렬화.
 *       Jackson polymorphism을 쓰지 않으므로 producer/consumer 모두 직접 직렬화한다.
 * </ul>
 */
@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamListenerContainer(
            RedisConnectionFactory connectionFactory) {

        StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(1))
                        .build();

        return StreamMessageListenerContainer.create(connectionFactory, options);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
