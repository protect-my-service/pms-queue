# Project Context

## What this project is

A real-time event ingestion service. Clients POST events (clicks, logs, metrics) to an HTTP endpoint. The service queues them and processes them asynchronously. This is the core pattern behind analytics pipelines, audit logs, and telemetry systems.

## Why this architecture

The naive approach — receive request, process immediately, respond — breaks under load. Processing is often slow (DB writes, external calls). Queuing decouples receiving from processing: the HTTP layer stays fast, the processing layer works at its own pace.

When the queue fills up, the system pushes back with `429 Too Many Requests` instead of crashing. That's backpressure.

## Learning goals

- Understand message queue architecture and the producer-consumer pattern
- Understand backpressure: what it is, why it matters, how to implement it
- Learn Spring Boot incrementally through building a real system

## Tech stack

- **Java 17** + **Spring Boot 3.2**
- **Gradle** (build tool + dependency management)
- **Embedded Tomcat** (HTTP server, bundled in spring-boot-starter-web)
- **Jackson** (JSON serialization/deserialization, bundled in spring-boot-starter-web)

## Build phases

| Phase | Focus | Status |
|---|---|---|
| 1 | Spring Boot basics — REST endpoint, JSON deserialization | Done |
| 2 | In-memory queue — producer/consumer, backpressure with BlockingQueue | Done |
| 3 | Persistence — store processed events with Spring Data JPA + H2 | Pending |
| 4 | External queue — replace BlockingQueue with Redis Streams | Pending |
| 5 | Backpressure patterns — rate limiting, circuit breaker, Retry-After | Pending |

## Project structure

```
src/main/java/org/example/
├── Main.java            # Spring Boot entry point
├── Event.java           # Event model (type, payload)
├── EventController.java # POST /events — accepts events, queues them
├── EventQueue.java      # Wraps ArrayBlockingQueue, capacity 100
└── EventWorker.java     # Background thread — drains and processes queue
```