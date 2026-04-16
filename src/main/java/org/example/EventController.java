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

    private final RedisEventProducer producer;
    private final EventRepository eventRepository;

    public EventController(RedisEventProducer producer, EventRepository eventRepository) {
        this.producer = producer;
        this.eventRepository = eventRepository;
    }

    @PostMapping
    public ResponseEntity<String> receiveEvent(@RequestBody Event event) {
        event.setReceivedAt(Instant.now());
        producer.publish(event);
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