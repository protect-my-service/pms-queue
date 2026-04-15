package org.example;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class EventWorker {

    private final EventQueue eventQueue;
    private final EventRepository eventRepository;

    public EventWorker(EventQueue eventQueue, EventRepository eventRepository) {
        this.eventQueue = eventQueue;
        this.eventRepository = eventRepository;
    }

    @PostConstruct
    public void start() {
        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    Event event = eventQueue.take();
                    process(event);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        worker.setName("event-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void process(Event event) {
        try {
            Thread.sleep(100); // simulate slow processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        eventRepository.save(event);
        System.out.println("[worker] saved: " + event);
    }
}