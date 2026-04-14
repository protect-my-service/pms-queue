package org.example;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventQueue eventQueue;

    public EventController(EventQueue eventQueue) {
        this.eventQueue = eventQueue;
    }

    @PostMapping
    public ResponseEntity<String> receiveEvent(@RequestBody Event event) {
        boolean accepted = eventQueue.offer(event);
        if (!accepted) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Queue full");
        }
        return ResponseEntity.accepted().body("Accepted");
    }
}