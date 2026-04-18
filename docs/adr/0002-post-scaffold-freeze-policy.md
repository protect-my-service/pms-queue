# ADR-0002: Post-scaffold freeze policy — performance-only

**Date**: 2026-04-18
**Status**: accepted
**Deciders**: pms-queue study project

## Context

`pms-queue`는 공격팀과 방어팀이 **동일한 기능 계약 위에서** 성능을 개선하고 방어 전략을 실험하기 위한 베이스 코드다.

만약 스캐폴드 완료 이후에 신규 기능이나 구조적 리팩토링이 허용된다면:
- 양 팀의 출발선이 달라진다(공정성 훼손)
- 계약이 흔들리면 성능 비교가 무의미해진다
- 학습자가 "성능을 개선하기"보다 "기능을 바꿔 회피하기"에 빠지기 쉽다

따라서 스캐폴드 이후의 작업 범위를 명확히 고정한다.

## Decision

**스캐폴드 완료 이후에는 신규 기능 개발과 리팩토링을 별도 목표로 두지 않는다.**

고정되는 계약:
- REST API(엔드포인트, 요청/응답 스키마)
- 이벤트 스키마(`BaseEvent` 및 6개 구체 타입의 필드 구조)
- Redis Stream key (`events:page_view`, `events:click`, `events:search`, `events:purchase`, `events:api_error`)
- 에러 코드(`ErrorCode` enum)
- DB 저장 계약(`event_record` 테이블 필수 컬럼)

**이후 허용되는 작업은 성능 개선뿐이다.**

### 성능 개선의 예
- 요청 처리량 증가
- Redis Stream 적재/소비 지연 감소
- Consumer 병렬 처리 개선
- ACK, retry, drop 정책 튜닝
- click 이벤트 샘플링 비율 조정
- purchase 중복 처리 비용 감소
- api_error 폭증 시 다른 이벤트 처리 지연 방지
- DB insert 병목 개선
- RateLimiter, CircuitBreaker 설정값 조정

### 금지되는 작업의 예
- 새로운 이벤트 타입 추가
- 새로운 API 추가
- 기존 이벤트 payload 구조 변경
- Redis Stream key 변경
- 공통 프레임워크 재설계
- 컨슈머 구조 대규모 리팩토링
- 외부 계약을 바꾸는 예외 응답 형식 변경

**단, 성능 개선을 위해 필요한 최소한의 내부 코드 변경은 허용한다. 이 경우에도 외부 계약과 학습 시나리오는 변경하지 않는다.**

## Alternatives Considered

### Alternative 1: 리팩토링까지 허용
- **Pros**: 코드 품질 개선 기회.
- **Cons**: "리팩토링"의 범위가 모호해 경쟁 공정성을 해치기 쉽다. 양 팀이 서로 다른 구조 위에서 경쟁하게 됨.
- **Why not**: 공격/방어 비교의 주 변수가 "구조 설계"가 되어 학습 목표인 "성능 개선"이 희석된다.

### Alternative 2: 완전 동결 (성능 개선도 최소 변경만)
- **Pros**: 최대한의 공정성.
- **Cons**: 필요한 최적화까지 막혀 실험이 불가능해진다.
- **Why not**: 학습 의의를 없앤다.

## Consequences

### Positive
- 양 팀의 비교 기준이 동일하다(외부 계약 고정).
- 학습자가 구조 변경으로 도망치지 않고 성능 병목과 직면한다.
- ADR이 자체적으로 "변경 허용 여부" 판단의 근거가 된다.

### Negative
- 장기적으로 쌓이는 기술 부채를 해소할 공식 경로가 없다(본 프로젝트는 학습용이므로 수용).

### Risks
- "성능 개선에 필요한 최소 변경"의 해석이 팀마다 다를 수 있다. 모호한 경우 ADR 추가로 기록해 결정을 투명화한다.
