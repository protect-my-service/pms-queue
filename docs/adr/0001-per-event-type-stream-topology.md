# ADR-0001: Per-event-type Redis Stream topology

**Date**: 2026-04-18
**Status**: accepted
**Deciders**: pms-queue study project

## Context

본 프로젝트는 5개의 서로 다른 특성을 가진 이벤트를 수집한다.

- `page_view` — 볼륨 많음, 손실 허용, at-least-once
- `click` — 볼륨 매우 많음, 샘플링 대상
- `search_query` + `search_result_click` — window join 학습용
- `purchase` — 볼륨 적음, 손실 불가, 중복 방지(멱등)
- `api_error` — 에러 폭증 시 격리 필요

각 이벤트는 배달 의미론(delivery semantics), SLA, 실패 허용 수준이 전부 다르다. 또한 본 프로젝트는 공격팀/방어팀이 나뉘어 공격·방어를 실험하는 구조이므로, 한쪽 타입의 폭증이 다른 타입의 처리에 영향을 주지 않도록 분리 경계가 필요하다.

## Decision

이벤트 타입별로 **전용 Redis Stream**을 사용한다.

```
events:page_view
events:click
events:search          # search_query + search_result_click 공용 (join 용이)
events:purchase
events:api_error
```

`search_query`와 `search_result_click`은 예외적으로 같은 stream(`events:search`)을 공유한다 — window join을 단일 consumer에서 수행해야 하기 때문.

## Alternatives Considered

### Alternative 1: 단일 `events` stream + type 디스패치
- **Pros**: 단순. 컨슈머 1개에서 switch 분기.
- **Cons**: HOL(Head-Of-Line) blocking — `api_error` 폭증이 `purchase` 처리를 지연시킨다.
- **Why not**: 격리 시나리오의 핵심 학습 포인트가 사라진다.

### Alternative 2: 하이브리드 — `api_error`만 별도, 나머지는 단일 stream
- **Pros**: 최소 변경으로 격리 확보.
- **Cons**: 샘플링(click) 정책을 전체 stream에 적용하면 다른 타입까지 샘플링되는 문제.
- **Why not**: 타입별 정책(rate limit, 샘플링, 멱등)을 깔끔히 분리하기 어렵다.

## Consequences

### Positive
- 타입별 rate limit, 샘플링, 멱등 정책을 독립 튜닝 가능.
- 한 타입의 폭증이 다른 타입 처리에 영향을 주지 않는다(bulkhead).
- 공격팀이 특정 타입에 집중 부하를 가하는 시나리오가 자연스럽게 구현된다.

### Negative
- Consumer 수가 늘어나 운영 복잡도가 상승.
- Stream 5개에 대한 MAXLEN·백업·모니터링을 각자 구성해야 함.

### Risks
- `SEARCH_QUERY`와 `SEARCH_RESULT_CLICK`을 공용 stream으로 두면, join consumer가 장애일 때 두 이벤트 모두가 지연된다. 성능 개선 과제에서 별도 consumer instance 분리 고려.
