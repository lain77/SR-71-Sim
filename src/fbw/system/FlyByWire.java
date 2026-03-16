package fbw.system;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.joml.Vector3f;

public class FlyByWire {

    private boolean running = true;
    private FlightData state;

    private ScheduledExecutorService scheduler;
    private Vector3f posicao;
    private Vector3f direcao;

    private double throttle     = 50;
    private double wind         = 5;

    // Velocidades angulares — acumulam inércia
    private double pitchRate    = 0;  // graus/s atual
    private double rollRate     = 0;  // graus/s atual
    private double yawRate = 0;

    // Inputs do jogador (-1, 0, +1)
    private double pitchInput   = 0;
    private double rollInput    = 0;

    // G-force calculada
    private double gForce       = 1.0;
    private double prevVertSpeed = 0;

    // Temperatura do motor (0-100%)
    private double engineTemp   = 20.0;

    // Combustível (litros)
    private double fuel         = 100000.0;
    private static final double FUEL_BURN_BASE = 50.0; // L/s no idle

    // Escala: 1 unidade de mundo = ALT_SCALE pés de altitude
    private static final double ALT_SCALE = 3.0;

    // Histórico de velocidade para o gráfico (últimos 60 samples)
    private final double[] speedHistory = new double[60];
    private int historyIndex = 0;
    private int historyTick  = 0;

    private boolean dead     = false;
    private int     hp       = 1; // 1 hit = morte instantânea
    
    public FlyByWire() {
        // Altitude inicial: 20000 ft → posicao.y = 2000
        state   = new FlightData(20000, 1000, 0, 0, 0, 0);
        posicao = new Vector3f(0, (float)(20000 / ALT_SCALE), 0);
        direcao = new Vector3f(0, 0, -1);
        throttle = state.speed;
    }

    public void start() {
        running = true;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                update(0.016);      // era 0.1
                lastData = state.copy();
            }
        }, 0, 16, TimeUnit.MILLISECONDS);  // era 100
    }

    // ── INPUTS DO JOGADOR ─────────────────────────────────────────
    public void setPitchInput(double v) { pitchInput = Math.max(-1, Math.min(1, v)); }
    public void setRollInput(double v)  { rollInput  = Math.max(-1, Math.min(1, v)); }

    // Mantém compatibilidade com chamadas antigas
    public void climbAltitude(double p)    { setPitchInput(1);  }
    public void decreaseAltitude(double p) { setPitchInput(-1); }
    public void rollLeft()                 { setRollInput(-1);  }
    public void rollRight()                { setRollInput(1);   }
    public void neutralPitch()             { setPitchInput(0);  }
    public void neutralRoll()              { setRollInput(0);   }

    public void throttleUp(double power)   { throttle = Math.min(throttle + power * 10, 6000); }
    public void throttleDown(double power) { throttle = Math.max(throttle - power * 10, 0); }

    private void update(double dt) {
        final double MAX_ALT   = 85000;
        final double MAX_SPEED = 6000;
        final double GRAVITY   = 9.8;

        // ══════════════════════════════════════════════════════════════
        // 1. FÍSICA ANGULAR COM INÉRCIA
        // ══════════════════════════════════════════════════════════════
        double maxPitchRate = 5.0;   // graus/s
        double maxRollRate  = 12.0;  // graus/s

        double pitchAccel = pitchInput * maxPitchRate * 5.0;
        double rollAccel  = rollInput  * maxRollRate  * 5.0;

        // Amortecimento: sem input, volta ao zero gradualmente
        if (pitchInput == 0) pitchAccel = -pitchRate * 3.0;
        if (rollInput  == 0) rollAccel  = -rollRate  * 3.0;

        pitchRate += pitchAccel * dt;
        rollRate  += rollAccel  * dt;

        pitchRate = Math.max(-maxPitchRate, Math.min(maxPitchRate, pitchRate));
        rollRate  = Math.max(-maxRollRate,  Math.min(maxRollRate,  rollRate));

        state.pitch += pitchRate * dt;
        state.roll  += rollRate  * dt;

        // Limites de atitude
        state.pitch = Math.max(-60, Math.min(60, state.pitch));
        state.roll  = Math.max(-90, Math.min(90, state.roll));

        // ══════════════════════════════════════════════════════════════
        // 2. CURVA COORDENADA — roll vira o avião de verdade
        // ══════════════════════════════════════════════════════════════
        // Fórmula real: turnRate = g * tan(bank) / V
        // Convertida para graus/s
        double targetYawRate = 0;
        if (state.speed > 30) {
            double bankRad = Math.toRadians(state.roll);
            double bankFactor = Math.sin(bankRad);
            targetYawRate = bankFactor * 12.0; // graus/s desejado
        }

        // Yaw rate com inércia — não muda instantaneamente
        double yawAccel = (targetYawRate - yawRate) * 2.0; // quão rápido converge
        yawRate += yawAccel * dt;
        yawRate = Math.max(-15, Math.min(15, yawRate));

        state.yaw += yawRate * dt;

        // ══════════════════════════════════════════════════════════════
        // 3. VELOCIDADE — throttle, arrasto, e GRAVIDADE
        // ══════════════════════════════════════════════════════════════
        // Arrasto quadrático
        double drag = 0.0005 * state.speed * state.speed;

        // Gravidade afeta velocidade: subir perde energia, descer ganha
        // Isso faz o avião desacelerar ao subir e acelerar ao mergulhar
        double gravEffect = GRAVITY * Math.sin(Math.toRadians(state.pitch)) * 2.5;

        state.speed += (throttle - drag - gravEffect) * dt * 0.1;
        state.speed  = Math.max(0, Math.min(MAX_SPEED, state.speed));

        // Mach
        state.mach = state.speed / 343.0;

        // ══════════════════════════════════════════════════════════════
        // 4. DIREÇÃO DE VOO — calculada do pitch + yaw
        // ══════════════════════════════════════════════════════════════
        // O avião VAI para onde está apontando
        double yawRad   = Math.toRadians(state.yaw);
        double pitchRad = Math.toRadians(state.pitch);

        float dx = (float)(Math.cos(pitchRad) * Math.sin(yawRad));
        float dy = (float)(Math.sin(pitchRad));
        float dz = (float)(-Math.cos(pitchRad) * Math.cos(yawRad));
        direcao.set(dx, dy, dz).normalize();

        // ══════════════════════════════════════════════════════════════
        // 5. MOVER POSIÇÃO ao longo da direção de voo
        // ══════════════════════════════════════════════════════════════
        posicao.add(new Vector3f(direcao).mul((float)(state.speed * dt * 3.0)));

        // ══════════════════════════════════════════════════════════════
        // 6. ALTITUDE VINDA DA POSIÇÃO (unificado!)
        // ══════════════════════════════════════════════════════════════
        // A altitude em pés é derivada da posição Y real
        double prevAlt = state.altitude;
        state.altitude = posicao.y * ALT_SCALE;

        // Clamp de altitude
        if (state.altitude < 0) {
            state.altitude = 0;
            posicao.y = 0;
        }
        if (state.altitude > MAX_ALT) {
            state.altitude = MAX_ALT;
            posicao.y = (float)(MAX_ALT / ALT_SCALE);
            // Impede de continuar subindo
            if (state.pitch > 0) state.pitch *= 0.95;
        }

        // ══════════════════════════════════════════════════════════════
        // 7. G-FORCE
        // ══════════════════════════════════════════════════════════════
        double vertSpeed = (state.altitude - prevAlt) / dt;
        double vertAccel = (vertSpeed - prevVertSpeed) / dt;
        gForce = 1.0 + vertAccel / GRAVITY;
        gForce = Math.max(-3, Math.min(9, gForce));
        prevVertSpeed = vertSpeed;

        // ══════════════════════════════════════════════════════════════
        // 8. TEMPERATURA DO MOTOR
        // ══════════════════════════════════════════════════════════════
        double targetTemp = 20 + (throttle / 6000.0) * 80.0;
        engineTemp += (targetTemp - engineTemp) * dt * 0.3;

        // ══════════════════════════════════════════════════════════════
        // 9. COMBUSTÍVEL
        // ══════════════════════════════════════════════════════════════
        double burnRate = FUEL_BURN_BASE * (throttle / 1000.0 + 0.5);
        fuel = Math.max(0, fuel - burnRate * dt);
        if (fuel <= 0) throttle = 0;

        // ══════════════════════════════════════════════════════════════
        // 10. HISTÓRICO DE VELOCIDADE
        // ══════════════════════════════════════════════════════════════
        historyTick++;
        if (historyTick >= 3) {
            speedHistory[historyIndex % 60] = state.speed;
            historyIndex++;
            historyTick = 0;
        }
    }
    
    public void kill() {
        dead     = true;
        throttle = 0;
        pitchRate = -15.0;
    }

    // ── GETTERS ───────────────────────────────────────────────────
    public double getGForce()         { return gForce; }
    public double getEngineTemp()     { return engineTemp; }
    public double getFuel()           { return fuel; }
    public double[] getSpeedHistory() { return speedHistory; }
    public int getHistoryIndex()      { return historyIndex; }

    public void applyTurbulence(double pitchDelta, double rollDelta) {
        pitchRate += pitchDelta * 10;
        rollRate  += rollDelta  * 10;
    }

    private volatile FlightData lastData;
    public FlightData getLastData() { return lastData; }
    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public static class FlightData {
        public double altitude, speed, pitch, roll, mach, yaw;

        public FlightData(double alt, double spd, double pt, double rl, double mc, double yw) {
            altitude = alt; speed = spd; pitch = pt; roll = rl; mach = mc; yaw = yw;
        }

        public FlightData copy() {
            return new FlightData(altitude, speed, pitch, roll, mach, yaw);
        }

        public double getAltitude() { return altitude; }
        public double getSpeed()    { return speed; }
        public double getPitch()    { return pitch; }
        public double getRoll()     { return roll; }
        public double getMach()     { return mach; }
        public double getYaw()      { return yaw; }
    }

    public double getThrottle()             { return throttle; }
    public void   setThrottle(double t)     { throttle = t; }
    public Vector3f getPosicao()            { return posicao; }
    public Vector3f getDirecao()            { return direcao; }
    public void setPosicao(Vector3f pos)    { if (pos != null) posicao.set(pos); }
    public void setDirecao(Vector3f dir)    { direcao = dir; }

    private boolean countermeasurePressed = false;
    public void activateCountermeasure()   { countermeasurePressed = true; }
    public void resetCountermeasure()      { countermeasurePressed = false; }
    public boolean isCountermeasurePressed() { return countermeasurePressed; }
    
    public boolean isDead() { return dead; }
    public void    revive() { dead = false; hp = 1; pitchRate = 0; rollRate = 0; }
}