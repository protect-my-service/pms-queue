package org.example.pmsqueue.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리 — RFC 7807 ProblemDetail 변환 허브.
 *
 * <p>시니어 노트:
 * <ul>
 *   <li>이 클래스는 "에러 계약"의 진짜 구현체다. 모든 예외는 여기를 거쳐야 하며,
 *       컨트롤러에서 try-catch로 에러 응답을 직접 생성하지 말 것.
 *   <li>Jackson validation(스키마 불일치) → {@link MethodArgumentNotValidException} →
 *       422 + 실패 필드 목록을 context에 담아 반환.
 *   <li>알 수 없는 예외는 500 + "SYS-0001"로 처리. 운영에서 내부 메시지 그대로 노출 금지.
 *       TODO(junior): traceId를 MDC/Sleuth로 연동하고, 응답의 "traceId" 필드를 실제 값으로 채우기.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_TYPE_PREFIX = "https://pms-queue.study/errors/";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex, WebRequest request) {
        return build(ex.errorCode(), ex.getMessage(), request, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        EventSchemaException mapped = new EventSchemaException(detail);
        return build(mapped.errorCode(), detail, request, mapped);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnknown(Exception ex, WebRequest request) {
        // 시니어 노트: 운영 환경에서는 ex.getMessage() 노출이 정보 유출이 될 수 있다.
        // TODO(junior): profile별로 상세 메시지/일반 메시지를 선택하도록 분기.
        return build(ErrorCode.INTERNAL_ERROR, "Unexpected error", request, ex);
    }

    private ResponseEntity<ProblemDetail> build(ErrorCode code, String detail, WebRequest request, Throwable cause) {
        HttpStatus status = code.httpStatus();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(PROBLEM_TYPE_PREFIX + code.code().toLowerCase()));
        pd.setTitle(code.title());
        pd.setInstance(URI.create(request.getDescription(false).replace("uri=", "")));
        pd.setProperty("code", code.code());
        pd.setProperty("timestamp", Instant.now().toString());
        if (cause instanceof BusinessException be && !be.context().isEmpty()) {
            pd.setProperty("context", be.context());
        }
        // TODO(junior): traceId 연동 (Sleuth/Micrometer Observation).
        return ResponseEntity.status(status).body(pd);
    }

    private String formatFieldError(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }
}
