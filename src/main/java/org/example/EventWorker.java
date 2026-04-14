package org.example;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class EventWorker {

    private final EventQueue eventQueue;

    public EventWorker(EventQueue eventQueue) {
        this.eventQueue = eventQueue;
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
        System.out.println("[worker] processed: " + event);
    }
}