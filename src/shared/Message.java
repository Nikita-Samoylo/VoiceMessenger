// shared/Message.java
package shared;

public abstract class Message {
    protected String sender;

    public Message(String sender) {
        this.sender = sender;
    }

    public String getSender() {
        return sender;
    }

    public abstract String toJson();
}