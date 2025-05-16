package shared;

import java.time.Instant;

public abstract class Message {
    protected String sender;
    protected Instant timestamp;

    public Message(String sender) {
        this.sender = sender;
        this.timestamp = Instant.now();
    }

    public String getSender() {
        return sender;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public abstract String toJson();
}