package fbw.gameplay;

public class MissionEscape extends Mission {

    private final float requiredMach;   // Mach mínimo para escapar
    private float       highSpeedTime;  // tempo acima da velocidade
    private static final float REQUIRED = 8f; // 8s acima do Mach exigido

    private boolean missileWasActive = false;

    public MissionEscape(String name, float requiredMach) {
        this.name         = name;
        this.requiredMach = requiredMach;
        this.timeLimit    = 120f;
        this.briefing     = String.format(
            "Radar inimigo detectou voce!\n" +
            "Acelere para Mach %.1f e mantenha por 8 segundos\n" +
            "para escapar do envelope de interceptacao.",
            requiredMach);
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

        if (enemy.isMissileWarning()) {
            missileWasActive = true;
        }
    }

    @Override
    public String getHudStatus() {
        if (state == State.SUCCESS) return "ESCAPE REALIZADO!";
        if (state == State.FAILED)  return "ABATIDO - Missao falhou";

        float pct = (highSpeedTime / REQUIRED) * 100f;
        fbw.system.FlyByWire.FlightData data = null; // só para evitar null
        return String.format("FUGA | Mach %.1f req | Escape: %2.0f%% | %ds",
                             requiredMach, pct, remainingSeconds());
    }
}