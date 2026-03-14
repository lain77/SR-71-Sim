package fbw.system;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.joml.Vector3f;

import fbw.assets.Audio;

public class Enemy {

    private Audio audio = new Audio();
    private FlyData fly;
    private FlyByWire fbw;

    private boolean missileWarning = false;
    private boolean missileActive = false;
    private long missileStartTime = 0;
    private final long missileDuration = 8000; // 5 segundos de voo
    private ScheduledExecutorService scheduler;
    
    private Vector3f posicao = new Vector3f(0, 2000f, 5000f); // posição inicial do inimigo

    public Enemy(FlyData fly, FlyByWire fbw) {
        this.fly = fly;
        this.fbw = fbw;
    }

    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        
        missileActive   = false;
        missileWarning  = false;
        
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
                    Vector3f playerPos = fbw.getPosicao();
                    Vector3f dir = new Vector3f(playerPos).sub(posicao).normalize();
                    posicao.add(new Vector3f(dir).mul(50f));
                }
                return; // só sai se o míssil não foi lançado
            }

            // ---------- MÍSSIL ATIVO ----------
            long elapsed = System.currentTimeMillis() - missileStartTime;

         // Move o inimigo em direção ao jogador
            Vector3f playerPos = fbw.getPosicao();
            Vector3f dir = new Vector3f(playerPos).sub(posicao).normalize();
            posicao.add(new Vector3f(dir).mul(50f));

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
                missileActive  = false;
                missileWarning = false;
                audio.stopSound();
                fbw.resetCountermeasure();

                // Míssil acertou — mata o jogador
                fbw.kill();
                System.out.println("Abatido pelo míssil!");
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
    
    public Vector3f getPosition() {
        return posicao;
    }
}
