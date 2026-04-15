# Phase 2: In-Memory Queue

## What was built

Events are now queued asynchronously. The controller returns immediately after enqueuing. A background worker drains the queue independently.

```
POST /events → 202 Accepted   (event queued)
POST /events → 429 Too Many Requests  (queue full — backpressure)
```

## Files added/modified

- `EventQueue.java` — wraps `ArrayBlockingQueue` with capacity 100
- `EventWorker.java` — background thread, drains queue via `take()`
- `EventController.java` — updated to use `offer()`, returns 429 when full

## Key concepts

### Why queue instead of processing inline

Processing inline (synchronous) means the HTTP thread waits for processing to complete. Under load, threads pile up and the server falls over. A queue decouples receiving from processing — the HTTP layer stays fast regardless of how slow processing is.

```
Before: request → process → respond        (client waits for processing)
After:  request → enqueue → respond        (client gets 202 immediately)
                     ↓
              worker processes later
```

### BlockingQueue methods

| Method | Queue full? | Queue empty? | Use when |
|---|---|---|---|
| `offer(item)` | returns `false` immediately | — | HTTP handlers, real-time producers |
| `put(item)` | blocks until space available | — | Background importers, critical data |
| `take()` | — | blocks until item available | Worker/consumer loops |

### offer vs put — the backpressure tradeoff

- `offer` → reject fast, protect the caller (HTTP thread never stalls)
- `put` → slow down the producer, protect data integrity (nothing is dropped)

Which to use depends on: **can you afford to lose this event?**
- Analytics click → yes → `offer` + drop on full
- Payment event → no → `put` or client-side retry

### HTTP status semantics

- `200 OK` — processed, here's the result
- `202 Accepted` — queued, will be processed later (more honest for async flows)
- `429 Too Many Requests` — slow down, server is overwhelmed

### @PostConstruct

Runs after Spring has finished injecting all dependencies into the class. The right place to start background threads — doing it in the constructor risks running before injection is complete.

### Daemon threads

`worker.setDaemon(true)` — the thread dies automatically when the app shuts down. Without it, the `while(true)` loop would prevent the JVM from exiting.

### InterruptedException handling

The correct pattern when a thread is interrupted:
```java
} catch (InterruptedException e) {
    Thread.currentThread().interrupt(); // restore interrupted status
    break;                              // exit the loop
}
```
Calling `Thread.currentThread().interrupt()` restores the interrupted flag so higher-level code can detect the interruption.

### Singleton scope

`@Component` (and `@Service`, `@Repository`) creates a single instance shared across the whole app. This is why the controller and worker share the *same* `EventQueue` — they both receive the same instance via dependency injection.

## Observed behavior

- Under normal load: events arrive, worker prints them out of order (non-deterministic thread scheduling — expected)
- Under burst load (110 parallel requests, worker sleeping 100ms): queue fills, some requests get 429, worker continues draining after burst ends

## Gotchas

- Worker processing must be artificially slowed (`Thread.sleep`) to observe backpressure — a bare `println` drains the queue faster than 110 parallel requests fill it
- Shell: `done \` followed by `wait` on the next line causes a zsh parse error. `wait` must follow `done` with no backslash, or everything must be on one line.
- Arrival order ≠ submission order in concurrent systems. Out-of-order processing logs are normal and expected.

## Capacity of other systems (for reference)

| System | Where capacity is set |
|---|---|
| Our `ArrayBlockingQueue` | Code: `CAPACITY = 100` |
| Tomcat thread pool | `application.properties`: `server.tomcat.threads.max` (default 200) |
| HikariCP DB connection pool | Config (default 10 connections) |
| Kafka | Infrastructure level, effectively disk-bounded |
| Redis Streams | Configurable max length per stream |