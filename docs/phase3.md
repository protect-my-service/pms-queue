# Phase 3: Persistence

## What was built

Processed events are now saved to a database. GET endpoints return persisted events.

```
POST /events        → 202 Accepted (event queued)
GET  /events        → JSON array of all saved events
GET  /events/count  → total count
```

## Files added/modified

- `build.gradle.kts` — added `spring-boot-starter-data-jpa` and `h2` (runtimeOnly)
- `application.properties` — H2 datasource config, DDL auto, H2 console
- `Event.java` — added `@Entity`, `@Id`, `@GeneratedValue`, `receivedAt` field
- `EventRepository.java` — Spring Data JPA repository interface
- `EventController.java` — injected repository, added GET endpoints
- `EventWorker.java` — calls `eventRepository.save(event)` after processing

---

## What is Hibernate?

Hibernate is the actual engine that does the database work. The layering:

```
Your code (JPA annotations, Repository interfaces)
    ↓
JPA spec (jakarta.persistence) — standard Java API, just interfaces
    ↓
Hibernate — implements JPA, generates SQL, manages sessions
    ↓
JDBC spec (java.sql) — standard interface for talking to any database
    ↓
JDBC driver (H2 driver, PostgreSQL driver, etc.) — speaks the actual DB protocol
    ↓
Database
```

**JPA** = the spec (rules). **Hibernate** = the implementation (code that runs).

Analogy: JPA is like the JDBC spec — a contract. Hibernate is like a JDBC driver — the actual implementation. You could swap Hibernate for EclipseLink without changing your code. In practice nobody does.

### Why so many layers?

Each layer solves a different problem:

| Layer | Problem it solves |
|---|---|
| JPA | I don't want to write SQL — map my Java objects to tables |
| Hibernate | Implements JPA — generates the SQL for you |
| JDBC spec | I want to write SQL but not care which database I'm targeting |
| JDBC driver | Actually speaks the wire protocol of a specific database |

When you add `spring-boot-starter-data-jpa` + `h2`, you get all layers. Spring Boot wires everything automatically. You never touch JDBC or Hibernate directly.

When you switch to PostgreSQL later: swap the H2 driver for the PostgreSQL driver, change a few properties. Your JPA code stays identical.

---

## runtimeOnly vs implementation

- `implementation` — needed at compile time and runtime (your code imports it)
- `runtimeOnly` — only needed when running, not at compile time

Database drivers are always `runtimeOnly`. Your code never imports H2 classes — it only talks to JDBC interfaces (`Connection`, `PreparedStatement`, etc.). The driver is only needed at runtime to fulfill those interfaces.

---

## @Entity

Marks a class as a database table. Hibernate creates the table from the class fields (`ddl-auto=create-drop` in dev).

Field naming: `receivedAt` (Java camelCase) → `RECEIVED_AT` (SQL UPPER_SNAKE_CASE) — automatic convention.

---

## @Id and @GeneratedValue

Every entity needs a primary key. `@GeneratedValue` controls how it's generated.

### Strategies

| Strategy | How it works | Use when |
|---|---|---|
| `IDENTITY` | Database auto-increments | Most common for relational DBs |
| `SEQUENCE` | DB sequence, Hibernate batches allocation | High insert volume, PostgreSQL |
| `UUID` | Globally unique string ID | Distributed systems, multiple servers inserting |
| `AUTO` | Hibernate picks — avoid, unpredictable | Don't use |

**IDENTITY vs SEQUENCE:** `SEQUENCE` lets Hibernate pre-allocate a batch of IDs (e.g. grab 50 at once) without hitting the DB each insert — better performance under high insert volume. For most apps `IDENTITY` is fine.

**UUID** matters in distributed systems — numeric auto-increment breaks when two separate servers insert rows because their counters collide. UUIDs are globally unique by design.

### Is Long standard for IDs?

Yes. `Integer` (32-bit) maxes at ~2 billion rows — a busy production table can hit that. `Long` (64-bit) maxes at ~9 quintillion — effectively unlimited.

`String` is used when the ID is a UUID:
```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private String id;
```

---

## Spring Data JPA repository

Extend `JpaRepository<Entity, IdType>` — Spring generates a full implementation at startup. No code to write.

### Built-in methods
`save`, `findById`, `findAll`, `deleteById`, `count` — zero SQL needed.

### Custom queries via method naming

Spring Data reads the method name and generates the query:
```java
List<Event> findByType(String type);
// → SELECT * FROM event WHERE type = ?

List<Event> findByTypeOrderByIdDesc(String type);
// → SELECT * FROM event WHERE type = ? ORDER BY id DESC

long countByType(String type);
// → SELECT COUNT(*) FROM event WHERE type = ?
```

### When to use @Query

When the method name would get absurd:
```java
// naming convention — unreadable
List<Event> findByTypeAndPayloadContainingAndIdGreaterThan(...);

// @Query — clearer
@Query("SELECT e FROM Event e WHERE e.type = :type AND e.payload LIKE %:keyword%")
List<Event> findFiltered(@Param("type") String type, @Param("keyword") String keyword);
```

**Rule of thumb:** built-in methods → naming convention → `@Query` for complex cases.

### Business logic vs repository

Repository = only data access (how to talk to DB).
Service (`@Service`) = what to do with data — validation, combining multiple repo calls, notifying other systems.

Don't put business logic in the repository.

---

## application.properties

Central Spring Boot configuration file. Everything tunable without touching code.

```properties
spring.datasource.url=jdbc:h2:mem:eventsdb   # in-memory H2 DB
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop    # auto-create tables on startup, drop on shutdown
spring.h2.console.enabled=true               # browser SQL console at /h2-console
```

**ddl-auto values:**
- `create-drop` — dev only, schema recreated every run, data lost on restart
- `validate` — production, Hibernate checks schema matches entities but never modifies it
- Schema changes in production are handled by migration tools (Flyway, Liquibase) — not Hibernate

Later phases add Redis config, pool sizes, and more here.

---

## H2 console

Browser SQL console at `http://localhost:8080/h2-console`.
- JDBC URL: `jdbc:h2:mem:eventsdb` — must use `:` not `/`
- Username: `sa`, Password: (empty)

Useful for inspecting raw table data, running ad-hoc SQL during development.

---

## Timestamps and event ordering

### receivedAt — why it was added

Events were arriving with IDs out of submission order. The ID reflects insert order (when the worker saved it), not submission order. `receivedAt` captures the moment the server received the request — stamped at the controller before queuing, before any processing delay.

### Why receivedAt doesn't solve ordering under burst load

Under burst (110 parallel requests), many events arrive within microseconds. `Instant.now()` resolution means many events get the same timestamp. Ordering by `receivedAt` is still unreliable — events with identical timestamps are in undefined order.

IDs are also unreliable — they reflect worker thread scheduling, which is non-deterministic at the OS level.

### The three timestamps in event systems

| Timestamp | Set by | Meaning |
|---|---|---|
| `occurredAt` | Client | When the event actually happened |
| `receivedAt` | Server (controller) | When the server saw it |
| `savedAt` | Server (worker) | When written to DB |

`occurredAt` gives true event order but requires trusting the client clock (can be wrong or manipulated). Most systems keep both: client `occurredAt` for business logic, server `receivedAt` for debugging/infrastructure.

### What receivedAt is still useful for

Even though it's not a reliable ordering key under burst load, it's worth keeping:
- Detecting lag — "events submitted at 10:00, received at 10:05 — network issue?"
- Time-range queries — `findByReceivedAtAfter(Instant.now().minus(1, HOURS))`
- Measuring queue latency — compare `receivedAt` to when worker saved

**Rule:** In async systems, stamp timestamps as early as possible — at the system boundary (when data enters), not deep in the processing pipeline.

---

## Gotchas

- H2 console JDBC URL: `jdbc:h2:mem:eventsdb` with colons — not slashes. Console defaults to a file-based URL; clear and retype it manually.
- `create-drop` drops schema on shutdown — all data lost on restart. Intentional for H2 dev setup.
- Timestamps don't reliably order events under burst load — events arriving within microseconds get effectively the same timestamp.
- IDs don't reflect submission order — they reflect the order the worker thread happened to process and save events (OS scheduling, non-deterministic).
