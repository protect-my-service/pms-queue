package org.example.pmsqueue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 애플리케이션 진입점.
 *
 * <p>시니어 노트: {@code @SpringBootApplication}이 {@code org.example.pmsqueue} 이하를 스캔한다.
 * 구 플랫 패키지({@code org.example})에 남은 클래스는 이 스캔 범위 밖이며, 삭제 예정.
 */
@SpringBootApplication
public class PmsQueueApplication {

    public static void main(String[] args) {
        SpringApplication.run(PmsQueueApplication.class, args);
    }
}
