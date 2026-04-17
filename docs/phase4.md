# Phase 4: Redis Streams

## What was built

Replaced the in-memory `BlockingQueue` with Redis Streams. Events now survive app restarts and are processed with at-least-once delivery guarantee.

```
POST /events  → XADD to Redis stream → 202 Accepted
              → consumer group reads via XREADGROUP
              → saves to DB
              → XACK
GET  /events  → returns all saved events from DB
```

## Files added/modified

- `build.gradle.kts` — added `spring-boot-starter-data-redis`
- `application.properties` — added Redis host/port config
- `RedisEventProducer.java` — publishes events to Redis stream via XADD
- `RedisConfig.java` — configures `ObjectHashMapper` and `StreamMessageListenerContainer`
- `RedisEventConsumer.java` — consumes from stream via consumer group, saves to DB, ACKs
- `EventController.java` — swapped `EventQueue` for `RedisEventProducer`
- `EventQueue.java`, `EventWorker.java` — deleted (replaced)

---

## Why replace BlockingQueue with Redis Streams

`BlockingQueue` lives in JVM memory:
- App restart → queue gone → unprocessed events lost
- Only works in a single process — can't share across multiple app instances

Redis Streams runs as a separate process:
- Events persist independently of your app
- Multiple consumer instances can share the same stream
- Events can be replayed from any point

---

## Redis fundamentals

### What is Redis?

An in-memory data store that runs as a separate process. Stores data as keys mapped to data structures: strings, lists, hashes, sets, streams, etc. No tables, no schema, no SQL — its own command language.

### Redis commands vs SQL

```
SQL:   INSERT INTO events (type, payload) VALUES ('click', 'button-1')
Redis: XADD events * type click payload button-1
```

`XADD events * type click payload button-1`:
- `events` — stream key
- `*` — auto-generate message ID
- `type click payload button-1` — field-value pairs

### Redis hash

One of Redis's built-in data structures — a map of string field names to string values under one key. A Stream message is stored as a Redis hash internally:

```
"_class"     → "org.example.Event"
"type"       → "click"
"payload"    → "button-1"
"receivedAt" → "2026-04-16T22:53:55.040462Z"
```

Redis stores everything as strings — `Instant` gets serialized to its ISO 8601 string representation.

---

## Redis Streams concepts

### Stream key

A stream is just a Redis key. All events go into the key `"events"`.

### Message ID

Every message gets an auto-generated ID: `timestamp-sequence` e.g. `1776380035048-0`. Monotonically increasing — guaranteed ordering. Unlike `receivedAt` from Phase 3, this doesn't have the burst-collision problem.

### Consumer group

A named group that tracks which messages have been delivered and ACKed. Multiple workers in the same group each get different messages — parallel processing. Workers in different groups each get all messages independently — fan-out.

### Pending Entries List (PEL)

When a worker claims a message via `XREADGROUP`, Redis moves it to the PEL. It stays there until `XACK`. If the worker crashes before ACKing, the message stays in the PEL and gets redelivered on reconnect.

### Key commands

| Command | What it does |
|---|---|
| `XADD` | Append a message to the stream |
| `XREADGROUP` | Read next unprocessed message as a consumer in a group |
| `XACK` | Acknowledge — remove from PEL, mark done |
| `XLEN` | Number of messages in the stream |
| `XRANGE` | Read messages by ID range (`-` = start, `+` = end) |
| `XINFO GROUPS` | Inspect consumer group state |

---

## Delivery guarantees

| Mechanism | Guarantee | What it means |
|---|---|---|
| `BlockingQueue` (Phase 2) | At-most-once | Crash before processing = event lost |
| Redis Streams + XACK | At-least-once | Crash before ACK = event redelivered (possible duplicate) |
| Exactly-once | Hard | Requires idempotency on the consumer side |

**At-least-once flow:**
1. `XREADGROUP` — message moves to PEL
2. `process(event)` — save to DB
3. `XACK` — removed from PEL

Crash between 2 and 3 → message stays in PEL → redelivered on restart → saved again (duplicate row). Acceptable for most use cases. To prevent duplicates you'd make the consumer idempotent (check if event already exists before saving).

---

## Spring components

### ObjectHashMapper

Converts between Java objects and Redis hash field-value pairs. Spring uses this automatically when reading/writing stream messages. Also adds `_class` field to store the Java class name for deserialization.

### StringRedisTemplate

Spring's Redis client — auto-configured from `application.properties`. Handles connection management. Used for `XADD`, `XACK`, `createGroup`, etc.

### StreamMessageListenerContainer

Runs a background polling loop (equivalent to Phase 2's `while(true)` loop) that calls `XREADGROUP` on an interval and dispatches messages to your `StreamListener`.

- `pollTimeout` — how long to block waiting for messages per poll
- `targetType` — the Java class to deserialize messages into

### RedisConnectionFactory

Manages physical TCP connections to Redis. Connection pooling, reconnection. Auto-configured by Spring Boot. You inject it into components that need it (`StreamMessageListenerContainer`).

---

## @Configuration vs @Component

| | `@Component` | `@Configuration` |
|---|---|---|
| Use for | Your own classes | Creating/configuring third-party objects |
| How | Spring instantiates the class | You write `@Bean` factory methods |
| Proxy | No | Yes — `@Bean` method calls within the class return the same singleton |

`@Bean` — marks a method whose return value Spring should manage as a singleton. Used when you can't put `@Component` on a class (e.g. it's from a library).

---

## Healthy consumer group state (XINFO GROUPS)

```
name:              event-processors
consumers:         1
pending:           0     ← no unACKed messages — everything processed
last-delivered-id: ...   ← matches last message in stream
entries-read:      1
lag:               0     ← group is fully caught up
```

`pending > 0` means messages were delivered but not ACKed — either in-flight or stuck (worker crashed).

---

## Alternatives to Redis Streams

| | Redis Streams | Kafka | RabbitMQ |
|---|---|---|---|
| Setup | Simple | Complex (needs KRaft) | Moderate |
| Throughput | High | Very high (millions/sec) | Moderate |
| Retention | Configurable | Long-term disk | Deleted after ACK |
| Replay | Yes | Yes (by offset) | No |
| Use case | Mid-scale, low ops overhead | Large-scale pipelines | Task queues, RPC |

Redis Streams used here because: already a common dependency (caching, sessions), easy local setup, teaches the same concepts as Kafka (consumer groups, ACK, at-least-once). Kafka concepts transfer directly once you understand this.

---

## Gotchas

- `createGroup` throws if the group already exists — wrap in try/catch and ignore the exception.
- `ReadOffset.lastConsumed()` — consumer starts from where the group left off, not the beginning of the stream. On first run with an empty group, it reads only new messages.
- Redis stores everything as strings — complex types like `Instant` are serialized to their string form.
- No bounded capacity like `BlockingQueue(100)` — Redis accepts messages as long as it has memory. Backpressure strategy changes — covered in Phase 5.