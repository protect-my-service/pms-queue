package org.example.pmsqueue.common.exception;

/**
 * 이벤트 페이로드 검증 실패 (422).
 *
 * <p>시니어 노트: {@code @Valid} 가 잡지 못하는 교차 필드 검증·도메인 규칙 위반은
 * 서비스 레이어에서 이 예외를 던진다. 예: purchase의 {@code amount <= 0},
 * currency 화이트리스트 위반.
 */
public class EventSchemaException extends BusinessException {

    public EventSchemaException(String detail) {
        super(ErrorCode.EVENT_SCHEMA_INVALID, detail);
    }
}
