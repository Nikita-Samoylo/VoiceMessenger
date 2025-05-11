package client;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.time.Instant;

public class AudioRecorder {
    private static final float SAMPLE_RATE = 44100;
    private static final int SAMPLE_SIZE = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;

    private TargetDataLine line;
    private boolean isRecording;
    private ByteArrayOutputStream out;
    private Instant recordingStartTime;

    public void startRecording() {
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, SIGNED, BIG_ENDIAN);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            isRecording = true;
            out = new ByteArrayOutputStream();
            recordingStartTime = Instant.now();

            new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (isRecording) {
                    int count = line.read(buffer, 0, buffer.length);
                    if (count > 0) {
                        out.write(buffer, 0, count);
                    }
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public RecordingResult stopRecording() {
        isRecording = false;
        if (line != null) {
            line.stop();
            line.close();
        }
        Duration duration = Duration.between(recordingStartTime, Instant.now());
        return new RecordingResult(out.toByteArray(), duration.toMillis());
    }

    public static class RecordingResult {
        private final byte[] audioData;
        private final long durationMs;

        public RecordingResult(byte[] audioData, long durationMs) {
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
    }
}