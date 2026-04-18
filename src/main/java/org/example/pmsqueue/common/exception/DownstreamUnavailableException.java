package org.example.pmsqueue.common.exception;

/**
 * Downstream 의존성(Redis/DB) 장애 (503).
 *
 * <p>시니어 노트: CircuitBreaker가 열렸을 때 fallback에서 이 예외를 던진다.
 * 클라이언트는 {@code Retry-After}를 보고 재시도 여부를 결정한다.
 */
public class DownstreamUnavailableException extends BusinessException {

    public DownstreamUnavailableException(String dependencyName, Throwable cause) {
        super(ErrorCode.DOWNSTREAM_UNAVAILABLE, "Downstream unavailable: " + dependencyName, cause);
        with("dependency", dependencyName);
    }

    public DownstreamUnavailableException(String dependencyName) {
        super(ErrorCode.DOWNSTREAM_UNAVAILABLE, "Downstream unavailable: " + dependencyName);
        with("dependency", dependencyName);
    }
}
