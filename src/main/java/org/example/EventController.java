package org.example;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventQueue eventQueue;
    private final EventRepository eventRepository;

    public EventController(EventQueue eventQueue, EventRepository eventRepository) {
        this.eventQueue = eventQueue;
        this.eventRepository = eventRepository;
    }

    @PostMapping
    public ResponseEntity<String> receiveEvent(@RequestBody Event event) {
        event.setReceivedAt(Instant.now());
        boolean accepted = eventQueue.offer(event);
        if (!accepted) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Queue full");
        }
        return ResponseEntity.accepted().body("Accepted");
    }

    @GetMapping
    public List<Event> getAllEvents() {
        return eventRepository.findAll();
    }

    @GetMapping("/count")
    public long getEventsCount() {
        return eventRepository.count();
    }
}