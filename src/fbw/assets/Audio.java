package fbw.assets;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;

public class Audio {
    private Clip currentClip;
    private SourceDataLine musicLine;
    private Thread musicThread;
    private volatile boolean musicPlaying = false;

    public void playSound(String resourcePath) {
        new Thread(() -> {
            try {
                stopSound();
                InputStream audioSrc = getClass().getResourceAsStream(resourcePath);
                if (audioSrc == null) {
                    System.err.println("Arquivo de som não encontrado: " + resourcePath);
                    return;
                }
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(new BufferedInputStream(audioSrc));
                currentClip = AudioSystem.getClip();
                currentClip.open(audioStream);
                currentClip.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void playMusic(String resourcePath) {
        stopMusic();
        musicPlaying = true;

        musicThread = new Thread(() -> {
            try {
                while (musicPlaying) {
                    InputStream audioSrc = getClass().getResourceAsStream(resourcePath);
                    if (audioSrc == null) {
                        System.err.println("Música não encontrada: " + resourcePath);
                        return;
                    }
                    AudioInputStream audioStream = AudioSystem.getAudioInputStream(
                        new BufferedInputStream(audioSrc));

                    AudioFormat format = audioStream.getFormat();
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                    musicLine = (SourceDataLine) AudioSystem.getLine(info);
                    musicLine.open(format);

                    // Volume mais baixo
                    if (musicLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                        FloatControl vol = (FloatControl) musicLine.getControl(
                            FloatControl.Type.MASTER_GAIN);
                        vol.setValue(-10f);
                    }

                    musicLine.start();

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while (musicPlaying && (bytesRead = audioStream.read(buffer)) != -1) {
                        musicLine.write(buffer, 0, bytesRead);
                    }

                    musicLine.drain();
                    musicLine.close();
                    audioStream.close();
                    // Loop: volta pro while e toca de novo
                }
            } catch (Exception e) {
                if (musicPlaying) e.printStackTrace();
            }
        });
        musicThread.setDaemon(true);
        musicThread.start();
    }

    public void stopMusic() {
        musicPlaying = false;
        if (musicLine != null) {
            try {
                musicLine.stop();
                musicLine.close();
            } catch (Exception e) { /* ignore */ }
        }
        if (musicThread != null) {
            musicThread.interrupt();
            musicThread = null;
        }
    }

    public boolean isMusicPlaying() {
        return musicPlaying;
    }

    public void stopSound() {
        if (currentClip != null && currentClip.isRunning()) {
            currentClip.stop();
            currentClip.close();
        }
    }
}