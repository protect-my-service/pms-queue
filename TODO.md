# TODO

이 파일은 스캐폴드 **이후** 단계의 작업 목록입니다. 외부 계약(REST API, 이벤트 스키마, Stream key, 에러 코드, DB 저장 계약)은 고정이며, 아래 항목은 **성능 개선 범위 내에서** 수행합니다. (ADR-0002)

---

## 스캐폴드 완료 이후 정책

이 프로젝트는 공격팀과 방어팀이 동일한 기능 계약 위에서 성능을 개선하고 방어 전략을 실험하기 위한 베이스 코드입니다.

스캐폴드 완료 이후에는 **신규 기능 개발과 리팩토링을 별도 목표로 두지 않습니다**.
REST API, 이벤트 스키마, Redis Stream key, 에러 코드, DB 저장 계약은 고정합니다.
이후 허용되는 작업은 **성능 개선뿐**입니다.

**단, 성능 개선을 위해 필요한 최소한의 내부 코드 변경은 허용합니다.
이 경우에도 외부 계약과 학습 시나리오는 변경하지 않습니다.**

---

## 성능 개선 과제 (허용)

### 처리량 / 지연
- [ ] 요청 처리량(throughput) 증가 — JVM 튜닝, 비동기 엔드포인트
- [ ] Redis Stream 적재 지연 감소 — pipelining, 배치 XADD
- [ ] Redis Stream 소비 지연 감소 — polling 간격, read count 조정
- [ ] Consumer 병렬 처리 개선 — multi-instance, partitioning

### 정책 튜닝
- [ ] ACK/retry/drop 정책 튜닝 (특히 `PageViewConsumer`, `ApiErrorConsumer`)
- [ ] click 샘플링 비율 동적 조정 — 부하에 따라 1/10 ↔ 1/100 전환
- [ ] purchase 중복 처리 비용 감소 — 빠른 경로(UNIQUE 위반 전 캐시 확인)
- [ ] api_error 폭증 시 다른 이벤트 처리 지연 방지 — sliding window drop

### 저장소
- [ ] DB insert 병목 개선 — 배치 insert, 비동기 write
- [ ] JPA 2차 캐시 / 커넥션 풀 튜닝
- [ ] Redis Stream MAXLEN 추가 — approximate trim으로 메모리 제한

### Resilience
- [ ] RateLimiter 설정값 조정 (타입별 독립)
- [ ] CircuitBreaker sliding window / threshold 조정
- [ ] Bulkhead 패턴 추가 (Resilience4j `@Bulkhead`)
- [ ] Retry with exponential backoff

---

## 금지 범위 (참고)

다음은 스캐폴드 이후 **수행하지 않습니다** (ADR-0002).

- 새로운 이벤트 타입 추가
- 새로운 API 추가
- 기존 이벤트 payload 구조 변경
- Redis Stream key 변경
- 공통 프레임워크 재설계
- 컨슈머 구조 대규모 리팩토링
- 외부 계약을 바꾸는 예외 응답 형식 변경

---

## 코드에 남은 `TODO(junior)` 블록 빠른 참조

- `PurchaseConsumer` — ACK 타이밍, outbox 패턴 실험
- `ClickConsumer` — 샘플링 전략 선택·외부화
- `SearchJoinConsumer` — Redis Hash 기반 5분 윈도우 버퍼
- `ApiErrorConsumer` — 폭증 감지, 에러 분류 알림 분기
- `PageViewConsumer` — 손실 허용 재처리 한계 설정
- `RedisStreamEventProducer` — MAXLEN approximate trim
- `GlobalExceptionHandler` — traceId 주입, profile별 메시지 제어
- `BusinessException` — runtime stack trace 제어 플래그
