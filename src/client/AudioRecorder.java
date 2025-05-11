// client/AudioRecorder.java
package client;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;

public class AudioRecorder {
    private static final float SAMPLE_RATE = 44100;
    private static final int SAMPLE_SIZE = 16;
    private static final int CHANNELS = 1;
    private static final boolean SIGNED = true;
    private static final boolean BIG_ENDIAN = false;

    private TargetDataLine line;
    private boolean isRecording;
    private ByteArrayOutputStream out;

    public void startRecording() {
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE, CHANNELS, SIGNED, BIG_ENDIAN);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            isRecording = true;
            out = new ByteArrayOutputStream();

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

    public byte[] stopRecording() {
        isRecording = false;
        line.stop();
        line.close();
        return out.toByteArray();
    }
}