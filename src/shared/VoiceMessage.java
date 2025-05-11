package shared;

public class VoiceMessage extends Message {
    private byte[] audioData;
    private long durationMs;

    public VoiceMessage(String sender, byte[] audioData, long durationMs) {
        super(sender);
        this.audioData = audioData;
        this.durationMs = durationMs;
    }

    public byte[] getAudioData() {
        return audioData;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getFormattedDuration() {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / (1000 * 60)) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public String toJson() {
        return String.format("{\"type\":\"voice\",\"sender\":\"%s\",\"size\":%d,\"duration\":%d}",
                sender, audioData.length, durationMs);
    }
}