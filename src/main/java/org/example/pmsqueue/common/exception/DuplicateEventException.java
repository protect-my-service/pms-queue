package org.example.pmsqueue.common.exception;

/**
 * 동일 eventId 중복 처리 (409).
 *
 * <p>시니어 노트: purchase와 같은 exactly-once-like 의미론에서,
 * UNIQUE 제약 위반을 이 예외로 매핑해 멱등 처리 여부를 명시적으로 표현한다.
 * 컨슈머에서 이 예외를 잡으면 "이미 처리된 이벤트" 로그 + ACK만 수행하고 빠져나간다.
 */
public class DuplicateEventException extends BusinessException {

    public DuplicateEventException(String eventId) {
        super(ErrorCode.EVENT_DUPLICATE, "Duplicate eventId: " + eventId);
        with("eventId", eventId);
    }

    public DuplicateEventException(String eventId, Throwable cause) {
        super(ErrorCode.EVENT_DUPLICATE, "Duplicate eventId: " + eventId, cause);
        with("eventId", eventId);
    }
}
