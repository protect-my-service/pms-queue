# protect-my-service-queue

실시간 이벤트 수집 서비스를 통해 **message queue architecture**와 **backpressure 패턴**을 학습하기 위한 스터디 프로젝트입니다.

HTTP endpoint로 이벤트를 수신하고, Redis Stream에 적재한 뒤 consumer가 비동기로 처리합니다. 과부하 및 downstream 장애 상황에서 서비스를 보호하는 backpressure 패턴을 직접 관찰할 수 있습니다.

---

## 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Build | Gradle |
| Message Queue | Redis Streams |
| Redis Client | Lettuce |
| Resilience | Resilience4j 2.2.0 (RateLimiter, CircuitBreaker) |
| Infrastructure | Docker |

---

## 아키텍처 개요

```
Client
  │
  │  POST /events
  ▼
EventController
  ├─ RateLimiter  (초당 10건 초과 시 → 429 Too Many Requests)
  └─ CircuitBreaker  (Redis 장애 감지 시 → 503 Service Unavailable)
  │
  │  XADD
  ▼
Redis Stream ("events")
  │
  │  XREADGROUP
  ▼
RedisEventConsumer
  └─ 처리 완료 후 XACK
```

**Backpressure 계층:**

| 상황 | 응답 |
|---|---|
| 요청 과다 (초당 10건 초과) | 429 + `Retry-After: 1` |
| Redis 장애 (failure rate ≥ 50%) | 503 Service Unavailable |
| Redis 응답 지연 | 2초 timeout 후 실패 처리 |

---

## 실행 방법

**사전 요구사항:** Java 17+, Docker

```bash
# 1. Redis 실행
docker run -d --name redis-dev -p 6379:6379 redis:7

# 2. 앱 실행
./gradlew bootRun
```

앱이 정상 실행되면 `Tomcat started on port 8080` 로그가 출력됩니다.

---

## 핵심 도메인 및 API

### Event

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | String | 이벤트 유형 (예: `"click"`, `"login"`) |
| `payload` | String | 이벤트 데이터 |
| `receivedAt` | Instant | 서버 수신 시각 (controller에서 자동 설정) |

### API

| Method | Endpoint | 설명 | 응답 코드 |
|---|---|---|---|
| `POST` | `/events` | 이벤트 수신 및 Redis Stream 적재 | 202 / 429 / 503 |
| `GET` | `/events` | Redis Stream 전체 조회 | 200 |

### 호출 예시

**이벤트 전송 (정상):**
```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"type":"click","payload":"button-1"}'
# → 202 Accepted
```

**Rate limit 발생 (11건 동시 전송):**
```bash
for i in $(seq 1 11); do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://localhost:8080/events \
    -H "Content-Type: application/json" \
    -d "{\"type\":\"click\",\"payload\":\"$i\"}" &
done; wait
# → 202 x10, 429 x1 (Retry-After: 1 헤더 포함)
```

**Redis Stream 조회:**
```bash
curl http://localhost:8080/events
```

### Redis CLI 직접 조회

```bash
docker exec -it redis-dev redis-cli

XRANGE events - +        # stream 전체 메시지 조회
XLEN events              # 메시지 수 조회
XINFO GROUPS events      # consumer group 상태 조회 (pending, lag 등)
```

---

## 테스트

Unit test 및 integration test는 작성되지 않았습니다.

---

## 스터디 실습 과제

직접 수정하거나 실험해보며 개념을 확인할 수 있는 과제입니다.

- [ ] **Consumer 수평 확장** — consumer 인스턴스를 여러 개 실행해 consumer group의 병렬 처리 동작 관찰
- [ ] **Client별 Rate limiting** — 현재는 전역 적용, client IP별로 분리 적용해보기
- [ ] **Circuit breaker 모니터링** — Spring Boot Actuator의 `/actuator/circuitbreakers` endpoint로 실시간 상태 조회
- [ ] **Redis Stream max length 설정** — `MAXLEN` 옵션으로 stream 크기 제한 추가