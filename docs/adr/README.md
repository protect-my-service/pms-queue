# Architecture Decision Records

이 디렉터리는 `pms-queue` 프로젝트의 구조적 결정 사항을 기록합니다. Non-obvious한 설계 결정만 골라 남기며, 표준 선택(Spring Boot, JPA 등)은 README에서 설명합니다.

| ADR | 제목 | 상태 | 날짜 |
|-----|------|------|------|
| [0001](0001-per-event-type-stream-topology.md) | Per-event-type Redis Stream topology | accepted | 2026-04-18 |
| [0002](0002-post-scaffold-freeze-policy.md) | Post-scaffold freeze policy — performance-only | accepted | 2026-04-18 |

템플릿은 [`template.md`](template.md)를 복제해 사용합니다.
