# Phase 1: Spring Boot Basics

## What was built

A single HTTP endpoint that accepts a JSON event and echoes it back.

```
POST /events
{ "type": "click", "payload": "button-1" }
→ 200 OK: "OK: click"
```

## Files

- `build.gradle.kts` — Spring Boot plugins + web starter dependency
- `Main.java` — Spring Boot entry point
- `Event.java` — JSON model
- `EventController.java` — `POST /events` handler

## Key concepts

### Spring Boot plugins vs dependencies
- **Plugins** extend Gradle itself (how to build a runnable JAR)
- **Dependencies** are libraries your code uses at runtime
- `org.springframework.boot` plugin + `io.spring.dependency-management` handle versioning automatically — you don't specify versions on Spring dependencies

### Starters
Bundles of related libraries with sensible defaults. `spring-boot-starter-web` pulls in:
- **Spring MVC** — routing and controller framework
- **Tomcat** — embedded HTTP server (no separate installation)
- **Jackson** — JSON serialization/deserialization

### Auto-configuration
Spring Boot inspects your classpath and sets everything up automatically. Adding `spring-boot-starter-web` → HTTP server starts on port 8080 with zero config.

### @SpringBootApplication
Combines three annotations:
- `@SpringBootConfiguration` — marks this class as a configuration source
- `@EnableAutoConfiguration` — triggers auto-configuration
- `@ComponentScan` — scans this package and sub-packages for Spring-managed classes

### @ComponentScan gotcha
Spring only scans `org.example` and its sub-packages because `Main.java` lives there. A class in `org.other` would be invisible to Spring. **All your classes must be under the same base package as Main.**

### @RestController
Marks a class as an HTTP request handler. Return values are written directly to the HTTP response body.

### @RequestBody + Jackson deserialization
`@RequestBody` tells Spring to convert the JSON request body into a Java object. Jackson does the conversion by calling setters matching the JSON field names.

```java
// incoming JSON: { "type": "click", "payload": "button-1" }
// Jackson calls: event.setType("click"), event.setPayload("button-1")
public ResponseEntity<String> receiveEvent(@RequestBody Event event) { ... }
```

### ResponseEntity
Lets you control the HTTP status code explicitly. Without it, Spring defaults to `200 OK` for everything.

### Dependency injection
Spring manages object creation. You declare what you need in a constructor — Spring finds the right instance and passes it in. You never call `new` on Spring-managed classes.

## Gotchas

- Missing `Content-Type: application/json` header → `415 Unsupported Media Type`. Spring needs the header to know to invoke Jackson.
- The `%` at the end of curl responses is zsh indicating no trailing newline — not an error.
- Gradle must be synced (refreshed) in IntelliJ after changing `build.gradle.kts`, otherwise the IDE won't resolve new dependencies.