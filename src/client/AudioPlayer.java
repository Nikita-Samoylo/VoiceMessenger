package client;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;

public class AudioPlayer {
    private static SourceDataLine line;
    private static boolean isPlaying = false;
    private static Thread playbackThread;

    public static synchronized void play(byte[] audioData) {
        if (isPlaying) {
            stop();
            return;
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
                    if (line != null) {
                        line.drain();
                        line.close();
                    }
                    isPlaying = false;
                }
            });

            playbackThread.start();
        } catch (Exception e) {
            System.err.println("Ошибка инициализации аудио: " + e.getMessage());
        }
    }

    public static synchronized void stop() {
        if (line != null && isPlaying) {
            isPlaying = false;
            line.stop();
            line.close();
            if (playbackThread != null) {
                playbackThread.interrupt();
            }
        }
    }

    public static boolean isPlaying() {
        return isPlaying;
    }
}