package shared;

import org.json.JSONObject;

public  class SystemMessage extends Message {
    private final String text;

    public SystemMessage(String text) {
        super("system");
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toJson() {
        return new JSONObject()
                .put("type", "system")
                .put("content", text)
                .toString();
    }
}
