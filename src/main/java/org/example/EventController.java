package org.example;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
public class EventController {

    @PostMapping
    public String receiveEvent(@RequestBody Event event) {
        System.out.println("Received: " + event);
        return "OK: " + event.getType();
    }
}