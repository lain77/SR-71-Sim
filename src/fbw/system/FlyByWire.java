package fbw.system;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.joml.Vector3f;

public class FlyByWire {

    private boolean running = true;
    private FlightData state;

    private Vector3f posicao;
    private Vector3f direcao;

    private double throttle     = 50;
    private double wind         = 5;

    // Velocidades angulares — acumulam inércia
    private double pitchRate    = 0;  // graus/s atual
    private double rollRate     = 0;  // graus/s atual

    // Inputs do jogador (-1, 0, +1)
    private double pitchInput   = 0;
    private double rollInput    = 0;

    // G-force calculada
    private double gForce       = 1.0;
    private double prevSpeedY   = 0;

    // Temperatura do motor (0-100%)
    private double engineTemp   = 20.0;

    // Combustível (litros)
    private double fuel         = 100000.0;
    private static final double FUEL_BURN_BASE = 50.0; // L/s no idle

    // Histórico de velocidade para o gráfico (últimos 60 samples)
    private final double[] speedHistory = new double[60];
    private int historyIndex = 0;
    private int historyTick  = 0;

    private boolean dead     = false;
    private int     hp       = 1; // 1 hit = morte instantânea
    
    public FlyByWire() {
        state   = new FlightData(45000, 1000, 0, 0, 0, 0);
        posicao = new Vector3f(0, 2000f, 0);
        direcao = new Vector3f(0, 0, -1);
        throttle = state.speed;
    }

    public void start() {
        running = true;
        
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Runnable loop = () -> {
            if (running) {
                update(0.1);
                lastData = state.copy();
            } else {
                scheduler.shutdown();
            }
        };
        scheduler.scheduleAtFixedRate(loop, 0, 100, TimeUnit.MILLISECONDS);
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

        // ── 1. FÍSICA ANGULAR COM INÉRCIA ─────────────────────────
        // Taxa máxima de rotação do SR-71 (~3 graus/s pitch, ~5 graus/s roll)
        double maxPitchRate = 4.0;
        double maxRollRate  = 8.0;

        // Aceleração angular: responde rápido ao input
        double pitchAccel = pitchInput * maxPitchRate * 6.0;
        double rollAccel  = rollInput  * maxRollRate  * 6.0;

        // Inércia: sem input, desacelera gradualmente
        if (pitchInput == 0) pitchAccel = -pitchRate * 3.0; // amortecimento
        if (rollInput  == 0) rollAccel  = -rollRate  * 3.0;

        pitchRate += pitchAccel * dt;
        rollRate  += rollAccel  * dt;

        // Clamp nas taxas máximas
        pitchRate = Math.max(-maxPitchRate, Math.min(maxPitchRate, pitchRate));
        rollRate  = Math.max(-maxRollRate,  Math.min(maxRollRate,  rollRate));

        state.pitch += pitchRate * dt;
        state.roll  += rollRate  * dt;

        // ── 2. CURVA COORDENADA (roll vira o avião) ───────────────
        // Quanto mais inclinado, mais rápido vira o yaw
        double bankAngleRad = Math.toRadians(state.roll);
        double turnRate     = (state.speed / 300.0) * Math.tan(bankAngleRad) * 0.8;
        state.yaw -= turnRate * dt;

        // ── 3. VELOCIDADE E ARRASTO ───────────────────────────────
        double drag = 0.0008 * state.speed * state.speed;
        state.speed += (throttle - drag) * dt * 0.1;
        state.speed = Math.max(0, Math.min(MAX_SPEED, state.speed));

        // Mach (velocidade do som ~343 m/s)
        state.mach = state.speed / 343.0;

        // ── 4. ALTITUDE ────────────────────────────────────────────
        double lift    = state.speed * Math.sin(Math.toRadians(state.pitch));
        double prevY   = state.altitude;
        state.altitude += lift * dt;
        state.altitude = Math.max(0, Math.min(MAX_ALT, state.altitude));

        // ── 5. G-FORCE ─────────────────────────────────────────────
        double accelY = (state.altitude - prevY) / dt - prevSpeedY;
        gForce        = 1.0 + accelY / 9.8;
        gForce        = Math.max(-3, Math.min(9, gForce)); // clamp realista SR-71
        prevSpeedY    = (state.altitude - prevY) / dt;

        // ── 6. LIMITES DE ATITUDE ──────────────────────────────────
        state.pitch = Math.max(-45, Math.min(45, state.pitch));
        state.roll  = Math.max(-90, Math.min(90, state.roll));

        // ── 7. TEMPERATURA DO MOTOR ────────────────────────────────
        double targetTemp = 20 + (throttle / 6000.0) * 80.0;
        engineTemp += (targetTemp - engineTemp) * dt * 0.3; // aquece/esfria gradual

        // ── 8. COMBUSTÍVEL ─────────────────────────────────────────
        double burnRate = FUEL_BURN_BASE * (throttle / 1000.0 + 0.5);
        fuel = Math.max(0, fuel - burnRate * dt);
        if (fuel <= 0) throttle = 0; // motor apaga

        // ── 9. POSIÇÃO 3D ─────────────────────────────────────────
        float dx = (float)(Math.cos(Math.toRadians(state.pitch)) * Math.sin(Math.toRadians(state.yaw)));
        float dy = (float) Math.sin(Math.toRadians(state.pitch));
        float dz = (float)(-Math.cos(Math.toRadians(state.pitch)) * Math.cos(Math.toRadians(state.yaw)));
        direcao.set(dx, dy, dz).normalize();
        posicao.add(new Vector3f(direcao).mul((float)(state.speed * dt)));

        // ── 10. HISTÓRICO DE VELOCIDADE ────────────────────────────
        historyTick++;
        if (historyTick >= 3) { // amostra a cada 300ms
            speedHistory[historyIndex % 60] = state.speed;
            historyIndex++;
            historyTick = 0;
        }
    }
    
    public void kill() {
        dead     = true;
        throttle = 0;
        // Faz o avião cair dramaticamente
        pitchRate = -15.0;
    }

    // ── GETTERS NOVOS ─────────────────────────────────────────────
    public double getGForce()      { return gForce; }
    public double getEngineTemp()  { return engineTemp; }
    public double getFuel()        { return fuel; }
    public double[] getSpeedHistory() { return speedHistory; }
    public int getHistoryIndex()   { return historyIndex; }

    public void applyTurbulence(double pitchDelta, double rollDelta) {
        pitchRate += pitchDelta * 10;
        rollRate  += rollDelta  * 10;
    }

    private volatile FlightData lastData;
    public FlightData getLastData() { return lastData; }
    public void stop()              { running = false; }

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