package shared;

import java.util.Arrays;
import java.util.Base64;

public class VoiceMessage extends Message {
    private byte[] audioData;
    private long durationMs;
    public static final int MAX_AUDIO_DURATION_MS = 30000; // 30 секунд максимум
    public static final int MAX_AUDIO_SIZE_BYTES = 1024 * 1024; // 1MB максимум

    public VoiceMessage(String sender, byte[] audioData, long durationMs) {
        super(sender);
        if (!isValid(audioData, durationMs)) {
            throw new IllegalArgumentException("Voice message exceeds size or duration limits");
        }
        this.audioData = audioData;
        this.durationMs = durationMs;
    }

    public static boolean isValid(byte[] audioData, long durationMs) {
        return audioData != null &&
                audioData.length > 0 &&
                audioData.length <= MAX_AUDIO_SIZE_BYTES &&
                durationMs > 0 &&
                durationMs <= MAX_AUDIO_DURATION_MS;
    }

    public byte[] getAudioData() {
        return audioData;
    }

    public long getDurationMs() {
        return durationMs;
    }

    @Override
    public String toJson() {
        return String.format("{\"type\":\"voice\",\"sender\":\"%s\",\"size\":%d,\"duration\":%d,\"timestamp\":\"%s\"}",
                sender, audioData.length, durationMs, timestamp);
    }

    public String toHistoryJson() {
        // Заменяем Base64 на прямое сохранение байтов
        return String.format("{\"type\":\"voice\",\"sender\":\"%s\",\"durationMs\":%d,\"audioData\":%s,\"timestamp\":\"%s\"}",
                sender,
                durationMs,
                Arrays.toString(audioData), // Сохраняем как массив байтов
                timestamp);
    }
}