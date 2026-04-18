# pms-queue

공격팀 vs 방어팀이 **동일한 기능 계약 위에서** 성능을 개선하고 방어 전략을 실험하기 위한 이벤트 수집 학습용 베이스 코드입니다.

주니어 백엔드 엔지니어가 코드와 주석의 `TODO(junior)` 블록을 직접 채워가며 백엔드 시스템의 어려운 지점(멱등, 윈도우 조인, 샘플링, 격리, 배달 의미론)을 학습하도록 설계되어 있습니다.

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.1 |
| Build | Gradle |
| Message Queue | Redis Streams |
| Redis Client | Lettuce |
| DB | H2 (인메모리, 학습 기본값) — 팀별 인프라 구축 단계에서 교체 |
| ORM | Spring Data JPA |
| Resilience | Resilience4j 2.2.0 (타입별 RateLimiter · CircuitBreaker) |

---

## 아키텍처 개요

```
Client
  │ POST /api/v1/events/{type}
  ▼
EventIngestController (타입별 6개 핸들러)
  ├─ @Valid  → 422 ProblemDetail
  ├─ RateLimiter  → 429 ProblemDetail (RL-0001)
  └─ CircuitBreaker → 503 ProblemDetail (DS-0001)
  │
  ▼
RedisStreamEventProducer — EventType → stream key 라우팅
  │
  ▼
 events:page_view  ─▶ PageViewConsumer  (at-least-once, 손실 허용)
 events:click      ─▶ ClickConsumer     (샘플링)
 events:search     ─▶ SearchJoinConsumer (query + result_click 윈도우 조인)
 events:purchase   ─▶ PurchaseConsumer  (UNIQUE 기반 멱등)
 events:api_error  ─▶ ApiErrorConsumer  (격리, 폭증 방어 대상)

각 Consumer → JPA(EventRecordEntity) 원본 로그
```

**에러 계약**: 모든 에러는 RFC 7807 `ProblemDetail`로 반환. 에러 코드는 `ErrorCode` enum에서 중앙 관리.

---

## 이벤트 타입 개요

| 타입 | Stream Key | 배달 의미론 | 학습 포인트 |
|---|---|---|---|
| `page_view` | `events:page_view` | at-least-once | 기준선 · 기본 consumer 구조 |
| `click` | `events:click` | best-effort | 1/N 샘플링 (해시/확률/reservoir 트레이드오프) |
| `search_query` | `events:search` | at-least-once | 윈도우 조인의 좌변 |
| `search_result_click` | `events:search` | at-least-once | 윈도우 조인의 우변 (watermark, late arrival) |
| `purchase` | `events:purchase` | 중복 방지(UNIQUE) | ACK 타이밍 딜레마 · outbox 개념 |
| `api_error` | `events:api_error` | at-least-once (격리) | bulkhead · 에러 폭증 방어 |

> **주의**: `purchase`의 "중복 방지"는 DB UNIQUE 제약 기반 멱등입니다. 분산 환경의 진짜 exactly-once는 별도 작업이며 이 스캐폴드의 범위 밖입니다.

---

## 실행 방법

**사전 요구사항**: Java 21+, Docker

```bash
# 1) Redis
docker run -d --name redis-dev -p 6379:6379 redis:7

# 2) 앱 실행 (H2 인메모리 DB 자동 기동)
./gradlew bootRun
```

앱이 기동되면 `Tomcat started on port 8080` 로그가 찍힙니다. H2 콘솔: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:pmsqueue`).

---

## API 예시

### 정상 호출 (page_view)
```bash
curl -X POST http://localhost:8080/api/v1/events/page_view \
  -H "Content-Type: application/json" \
  -d '{
    "eventId":"11111111-1111-1111-1111-111111111111",
    "eventType":"PAGE_VIEW",
    "occurredAt":"2026-04-18T10:00:00Z",
    "userId":"u1","sessionId":"s1","traceId":"t1",
    "url":"/home"
  }'
# → 202 Accepted
```

### purchase (중복 방지 시나리오)
```bash
# 동일 eventId로 두 번 POST → 두 번째는 consumer에서 DuplicateEventException 잡혀 ACK만 수행
curl -X POST http://localhost:8080/api/v1/events/purchase \
  -H "Content-Type: application/json" \
  -d '{
    "eventId":"22222222-2222-2222-2222-222222222222",
    "eventType":"PURCHASE",
    "occurredAt":"2026-04-18T10:00:00Z",
    "userId":"u1","sessionId":"s1","traceId":"t1",
    "orderId":"O-1001","amount":9900,"currency":"KRW"
  }'
```

### 스키마 검증 실패 (ProblemDetail 422)
```bash
curl -X POST http://localhost:8080/api/v1/events/purchase \
  -H "Content-Type: application/json" \
  -d '{"eventId":"x","eventType":"PURCHASE","occurredAt":"2026-04-18T10:00:00Z","orderId":"O1","amount":-100,"currency":"KRW"}'
# → 422 application/problem+json
# {"type":".../evt-0001","title":"Event schema validation failed","status":422,"code":"EVT-0001",...}
```

### Rate limit 시연 (click 500/s 초과)
```bash
for i in $(seq 1 600); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://localhost:8080/api/v1/events/click \
    -H "Content-Type: application/json" \
    -d "{\"eventId\":\"c-$i\",\"eventType\":\"CLICK\",\"occurredAt\":\"2026-04-18T10:00:00Z\",\"userId\":\"u1\",\"sessionId\":\"s1\",\"traceId\":\"t1\",\"elementId\":\"btn\",\"page\":\"/\"}" &
done; wait
# → 대부분 202, 일부 429 (code=RL-0001)
```

### Redis Stream 직접 확인
```bash
docker exec -it redis-dev redis-cli
XLEN events:page_view
XLEN events:click
XLEN events:purchase
XLEN events:api_error
XINFO GROUPS events:purchase
```

---

## 공격팀 / 방어팀 운영 가이드

이 프로젝트는 **동일 베이스 코드에서 양 팀이 인프라를 구축해 경쟁**하는 구조입니다.

### 공격팀의 기본 무기
- 대량 트래픽 (특히 `click`에 폭격)
- `api_error` 격리 스트림 폭증
- 동일 `eventId` 재전송으로 중복 처리 비용 증가
- 검색 `queryId` 비매칭으로 조인 버퍼 고갈

### 방어팀의 기본 도구
- 타입별 RateLimiter 임계치 튜닝 (`application.properties`)
- `click` 샘플링 비율 조정 (`ClickConsumer.SAMPLE_ONE_IN_N`)
- `api_error` 폭증 감지 시 drop/우회
- `purchase` 멱등성 체크 캐시화
- Consumer 병렬 인스턴스 추가 (수평 확장)

### 공통 규칙 (ADR-0002 참조)
- REST API, 이벤트 스키마, Stream key, 에러 코드, DB 저장 계약은 **고정**입니다.
- 이후 허용되는 작업은 **성능 개선**뿐이며, 신규 기능 추가와 구조 리팩토링은 별도 목표로 두지 않습니다.
- 단, 성능 개선에 필요한 최소한의 내부 코드 변경은 허용됩니다(외부 계약과 학습 시나리오는 유지).

---

## 학습 포인트 (TODO 블록 위치)

| 파일 | 학습 포인트 |
|---|---|
| `PurchaseConsumer` | ACK 타이밍 · outbox 패턴 · 분산 exactly-once의 한계 |
| `ClickConsumer` | 해시 vs 확률 vs reservoir 샘플링 트레이드오프 |
| `SearchJoinConsumer` | watermark · late arrival · out-of-order · 1:N 조인 |
| `ApiErrorConsumer` | sliding window 폭증 감지 · bulkhead 시연 |
| `PageViewConsumer` | at-least-once 재처리 정책 · 손실 허용 시나리오 |
| `GlobalExceptionHandler` | traceId 연동 · profile별 메시지 노출 제어 |
| `RedisStreamEventProducer` | MAXLEN 적용 · approximate trim |
| `RedisConfig` | 타입별 listener container 분리(성능 개선 과제) |

---

## 문서

- [docs/adr/](docs/adr/) — Architecture Decision Records (2개)
- [TODO.md](TODO.md) — 포스트 스캐폴드 정책 및 성능 개선 과제 목록
- [docs/context.md](docs/context.md) — 프로젝트 배경
