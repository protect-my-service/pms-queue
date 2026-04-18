package org.example.pmsqueue.common.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * 도메인/비즈니스 규칙 위반을 표현하는 공통 추상 예외.
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>모든 비즈니스 예외는 이 클래스를 상속한다. {@link GlobalExceptionHandler}가
 *       이 타입 하나만 잡으면 모든 정형 에러 응답을 만들 수 있다.
 *   <li>{@code context}는 ProblemDetail의 extension field로 노출할 수 있는
 *       추가 정보를 담는다 (예: 충돌한 eventId, 검증 실패한 필드명).
 *       절대 PII/credential을 담지 말 것.
 *   <li>운영 환경에서는 stack trace가 비용이 크다.
 *       TODO(junior): runtime에 stack trace를 끌 수 있는 플래그를 둘지 고민.
 * </ul>
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> context = new HashMap<>();

    protected BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    protected BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> context() {
        return context;
    }

    /** fluent helper — 필요 시 체이닝으로 컨텍스트 누적. */
    public BusinessException with(String key, Object value) {
        this.context.put(key, value);
        return this;
    }
}
