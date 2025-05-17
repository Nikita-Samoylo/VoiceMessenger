package shared;

public class TextMessage extends Message {
    private String text;
    public static final int MAX_TEXT_LENGTH = 500; // Максимальная длина текстового сообщения

    public TextMessage(String sender, String text) {
        super(sender);
        if (!isValid(text)) {
            throw new IllegalArgumentException("Text message exceeds maximum length");
        }
        this.text = text;
    }

    public static boolean isValid(String text) {
        return text != null && !text.isEmpty() && text.length() <= MAX_TEXT_LENGTH;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toJson() {
        return String.format("{\"type\":\"text\",\"sender\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}",
                sender, text.replace("\"", "\\\""), timestamp);
    }
}
