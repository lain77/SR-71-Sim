package fbw.system;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import fbw.assets.Audio;

public class Enemy {

    private Audio audio = new Audio();
    private FlyData fly;
    private FlyByWire fbw;

    private boolean missileWarning = false;
    private boolean missileActive = false;
    private long missileStartTime = 0;
    private final long missileDuration = 5000; // 5 segundos de voo
    private ScheduledExecutorService scheduler;

    public Enemy(FlyData fly, FlyByWire fbw) {
        this.fly = fly;
        this.fbw = fbw;
    }

    public void start() {
        scheduler = Executors.newScheduledThreadPool(1);

        Runnable missileTask = () -> {
            FlyByWire.FlightData data = fbw.getLastData();
            if (data == null) return;

            // ---------- CRIA MÍSSIL ----------
            if (!missileActive) {
                double chance = Math.random(); // 0.0 a 1.0
                if (chance <= 0.1 && data.getAltitude() <= 50000) { // 10% de chance
                    missileActive = true;
                    missileWarning = true;
                    missileStartTime = System.currentTimeMillis();
                    System.out.println("🚨 Missile incoming!");
                    audio.playSound("/audio/F-14-Tomcat-RWR-Sounds.wav");
                }
                return; // só sai se o míssil não foi lançado
            }

            // ---------- MÍSSIL ATIVO ----------
            long elapsed = System.currentTimeMillis() - missileStartTime;

            // Jogador ativou contra-medida
            if (fbw.isCountermeasurePressed() && elapsed < missileDuration) {
                missileActive = false;
                missileWarning = false;
                fbw.resetCountermeasure();
                System.out.println("Contra medida ativada!");
                audio.stopSound();
                fbw.resetCountermeasure();
            }
            // Tempo do míssil acabou
            else if (elapsed >= missileDuration) {
                missileActive = false;
                missileWarning = false;
                System.out.println("Você foi atingido!");
                audio.stopSound();
                fbw.resetCountermeasure();
            }
        };

        // Executa a cada 100ms para responder rápido à contra-medida
        scheduler.scheduleAtFixedRate(missileTask, 5000, 1000, TimeUnit.MILLISECONDS);
    }

    public boolean isMissileWarning() {
        return missileWarning;
    }

    public void stop() {
        scheduler.shutdown();
    }
}
