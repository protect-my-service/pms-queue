package org.example;

import java.time.Instant;

public class Event {

    private String type;
    private String payload;
    private Instant receivedAt;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    @Override
    public String toString() {
        return "Event{type='" + type + "', payload='" + payload + "', receivedAt=" + receivedAt + "}";
    }
}