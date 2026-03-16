package fbw.gameplay;

public class MissionEscape extends Mission {

    private final float requiredMach;
    private float       highSpeedTime;
    private static final float REQUIRED = 8f;

    private boolean missileWasActive = false;

    public MissionEscape(String name, float requiredMach) {
        this.name         = name;
        this.requiredMach = requiredMach;
        this.timeLimit    = 120f;
        this.briefing = String.format(
            "ALERTA VERMELHO\n\n" +
            "Radar inimigo tem lock no seu SR-71.\n" +
            "Misseis SAM ja foram lancados.\n\n" +
            "PROCEDIMENTO DE FUGA:\n" +
            "  1. Acelere para Mach %.1f+\n" +
            "  2. Mantenha por 8 segundos\n" +
            "  3. Use contra-medidas se necessario [S]\n\n" +
            "Nenhum missil ja conseguiu atingir\n" +
            "um SR-71. Nao seja o primeiro.",
            requiredMach);

        rsoEvents.add(new RSOEvent(2f,  "MISSILE LAUNCH! MISSILE LAUNCH!"));
        rsoEvents.add(new RSOEvent(8f,  "Multiple contacts — push it up!"));
        rsoEvents.add(new RSOEvent(20f, "Still tracking — keep accelerating!"));
    }

    @Override
    public void start() {
        super.start();
        highSpeedTime    = 0;
        missileWasActive = false;
    }

    @Override
    public void reset() {
        super.reset();
        highSpeedTime    = 0;
        missileWasActive = false;
    }

    @Override
    public void update(float delta, fbw.system.FlyByWire fbw, fbw.system.Enemy enemy) {
        if (state != State.ACTIVE) return;
        tickTimer(delta);

        fbw.system.FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        double mach = data.getMach();

        if (mach >= requiredMach) {
            highSpeedTime += delta;
            if (highSpeedTime >= REQUIRED) state = State.SUCCESS;
        } else {
            highSpeedTime = Math.max(0, highSpeedTime - delta * 0.3f);
        }

        // Dispara mísseis periodicamente
        if (elapsed > 3f && ((int)(elapsed) % 10 == 0)) {
            enemy.launchMissile();
        }

        if (enemy.isMissileWarning()) {
            missileWasActive = true;
        }
    }

    @Override
    public String getHudStatus() {
        if (state == State.SUCCESS) return "OUTRUN! MISSILE LOST — RTB";
        if (state == State.FAILED)  return "SHOT DOWN";

        float pct = (highSpeedTime / REQUIRED) * 100f;
        return String.format("!! EVADE !! | MACH %.1f+ REQ | ESCAPE: %2.0f%% | %ds",
                             requiredMach, pct, remainingSeconds());
    }
    
    public float getTargetMach() { return requiredMach; }
}