// shared/TextMessage.java
package shared;

public class TextMessage extends Message {
    private String text;

    public TextMessage(String sender, String text) {
        super(sender);
        this.text = text;
    }

    @Override
    public String toJson() {
        return String.format("{\"type\":\"text\",\"sender\":\"%s\",\"content\":\"%s\"}",
                sender, text.replace("\"", "\\\""));
    }
}