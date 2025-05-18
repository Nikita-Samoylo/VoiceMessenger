package client;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;

public class AudioPlayer {
    private static SourceDataLine line;
    private static volatile boolean isPlaying = false;
    private static Thread playbackThread;

    public static synchronized void play(byte[] audioData) {
        if (isPlaying) {
            stop();
        }

        try {
            AudioFormat format = new AudioFormat(44100, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            isPlaying = true;
            playbackThread = new Thread(() -> {
                try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while (isPlaying && (bytesRead = bais.read(buffer)) != -1) {
                        line.write(buffer, 0, bytesRead);
                    }
                } catch (Exception e) {
                    System.err.println("Ошибка воспроизведения: " + e.getMessage());
                } finally {
                    stop();
                }
            });
            playbackThread.start();
        } catch (Exception e) {
            System.err.println("Ошибка инициализации аудио: " + e.getMessage());
        }
    }

    public static synchronized void stop() {
        if (isPlaying) {
            isPlaying = false;

            if (line != null) {
                line.stop();
                line.flush();
                if (line.isOpen()) {
                    line.close();
                }
                line = null;
            }

            if (playbackThread != null) {
                playbackThread.interrupt();
                playbackThread = null;
            }
        }
    }

    public static boolean isPlaying() {
        return isPlaying;
    }
}