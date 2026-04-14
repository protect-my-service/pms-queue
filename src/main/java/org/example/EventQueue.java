package org.example;

import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Component
public class EventQueue {

    private static final int CAPACITY = 100;
    private final BlockingQueue<Event> queue = new ArrayBlockingQueue<>(CAPACITY);

    public boolean offer(Event event) {
        return queue.offer(event);
    }

    public Event take() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }
}