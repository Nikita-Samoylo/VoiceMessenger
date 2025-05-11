// shared/VoiceMessage.java
package shared;

public class VoiceMessage extends Message {
    private byte[] audioData;

    public VoiceMessage(String sender, byte[] audioData) {
        super(sender);
        this.audioData = audioData;
    }

    public byte[] getAudioData() {
        return audioData;
    }

    @Override
    public String toJson() {
        return String.format("{\"type\":\"voice\",\"sender\":\"%s\",\"size\":%d}",
                sender, audioData.length);
    }
}