package fbw.assets;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.InputStream;

public class Audio {

    private Clip currentClip; 

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

    public void stopSound() {
        if (currentClip != null && currentClip.isRunning()) {
            currentClip.stop();
            currentClip.close(); 
        }
    }
}
