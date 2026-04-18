package org.example.pmsqueue.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 중앙 집중식 에러 코드 정의.
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>Error code는 ProblemDetail의 "code" extension 필드로 응답 본문에 노출되고,
 *       운영팀/방어팀이 로그·알림 룰을 구성하는 식별자로 활용된다.
 *   <li>HTTP status와 의미가 1:1로 매핑되도록 관리하여 클라이언트가 code만으로
 *       상태를 추론할 수 있게 한다.
 *   <li>신규 에러 코드를 추가하면 외부 계약 변경이므로, 스캐폴드 이후에는
 *       코드 추가도 삼가야 한다 (ADR-0002 참조).
 * </ul>
 */
public enum ErrorCode {

    EVENT_SCHEMA_INVALID("EVT-0001", HttpStatus.UNPROCESSABLE_ENTITY, "Event schema validation failed"),
    EVENT_DUPLICATE("EVT-0002", HttpStatus.CONFLICT, "Duplicate event id"),
    RATE_LIMITED("RL-0001", HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"),
    DOWNSTREAM_UNAVAILABLE("DS-0001", HttpStatus.SERVICE_UNAVAILABLE, "Downstream dependency unavailable"),
    INTERNAL_ERROR("SYS-0001", HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final String code;
    private final HttpStatus httpStatus;
    private final String title;

    ErrorCode(String code, HttpStatus httpStatus, String title) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.title = title;
    }

    public String code() { return code; }
    public HttpStatus httpStatus() { return httpStatus; }
    public String title() { return title; }
}
