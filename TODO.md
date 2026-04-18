# TODO

개인 개선 목록입니다. 위에서부터 순서대로 진행하는 것을 권장합니다.

---

## 테스트 작성

- [ ] Unit test: `EventController`, `RedisEventProducer`, `RedisEventConsumer`
- [ ] Integration test: Testcontainers로 실제 Redis 컨테이너 사용
- [ ] 부하 테스트: rate limiter, circuit breaker 임계치 검증

---

## 운영 환경 전환

- [ ] Dockerfile 작성 및 Docker Compose로 앱 + Redis 통합 실행
- [ ] 실제 DB (PostgreSQL) 연동 — 이벤트 영구 저장
- [ ] Flyway로 schema migration 관리
- [ ] Structured logging (JSON 형식, correlation ID 포함)
- [ ] Secret/config 외부화 (Spring Cloud Config, AWS Parameter Store 등)
- [ ] Redis connection pool 튜닝 (Lettuce pool 설정)
- [ ] Circuit breaker 설정값 운영 기준으로 조정 (`wait-duration`, `sliding-window-size`)
- [ ] Spring Boot Actuator + Prometheus/Grafana 연동 — circuit breaker 상태 모니터링
- [ ] CI/CD 파이프라인 구성 (GitHub Actions — build, test, Docker image push)

---

## 현재 코드 한계

- **At-least-once 중복 처리 미방지** — consumer 재시작 시 동일 이벤트 재처리 가능. idempotency key 도입 필요
- **Consumer 단일 인스턴스** (`consumer-1`) — 수평 확장 미지원. 여러 인스턴스 실행 시 consumer name 충돌
- **Rate limiter 전역 적용** — client IP별 개별 제한 없음. `KeyResolver` 기반 분리 필요
- **Redis Stream max length 미설정** — 메모리 무제한 증가 가능. `MAXLEN` 옵션 추가 필요
- **`receivedAt` 기반 정렬 불완전** — burst 환경에서 동일 timestamp 충돌. client-side sequence number로 보완 필요

---

## 추가 학습

- **Idempotency** — at-least-once 환경에서 중복 처리 방지 전략
- **Retry with exponential backoff** — Resilience4j `@Retry`
- **Bulkhead pattern** — caller별 thread pool 격리, Resilience4j `@Bulkhead`
- **Testcontainers** — integration test에서 실제 Redis/DB 컨테이너 사용
- **Client-side sequence number** — 정확한 이벤트 순서 보장
- **Kafka** — Redis Streams와 동일 개념, 대규모 파이프라인에 적합. 개념 전환 용이