package fbw.gameplay;

public class MissionFlightWindow extends Mission {

    private final float minAlt, maxAlt;
    private final float minMach, maxMach;
    private final float requiredTime;

    private float timeInWindow = 0;
    private float bestTime     = 0;

    public MissionFlightWindow(String name,
                                float minAlt,  float maxAlt,
                                float minMach, float maxMach,
                                float requiredTime) {
        this.name         = name;
        this.minAlt       = minAlt;
        this.maxAlt       = maxAlt;
        this.minMach      = minMach;
        this.maxMach      = maxMach;
        this.requiredTime = requiredTime;
        this.timeLimit    = requiredTime * 3f;
        this.briefing     = String.format(
            "Mantenha o SR-71 na janela de voo por %.0f segundos.\n" +
            "Altitude: %.0f - %.0f u\n" +
            "Mach: %.1f - %.1f",
            requiredTime, minAlt, maxAlt, minMach, maxMach);
    }

    @Override
    public void update(float delta, fbw.system.FlyByWire fbw, fbw.system.Enemy enemy) {
        if (state != State.ACTIVE) return;
        tickTimer(delta);

        fbw.system.FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        float  alt  = (float) data.getAltitude();
        double mach = data.getMach();

        boolean inWindow = alt  >= minAlt  && alt  <= maxAlt
                        && mach >= minMach && mach <= maxMach;

        if (inWindow) {
            timeInWindow += delta;
            bestTime = Math.max(bestTime, timeInWindow);
            if (timeInWindow >= requiredTime) state = State.SUCCESS;
        } else {
            timeInWindow = Math.max(0, timeInWindow - delta);
        }
    }

    @Override
    public String getHudStatus() {
        if (state == State.SUCCESS) return "JANELA MANTIDA - Missao completa!";
        if (state == State.FAILED)  return "JANELA PERDIDA - Tempo esgotado";

        float pct = (timeInWindow / requiredTime) * 100f;
        return String.format("JANELA | %2.0f%% | %.0f-%.0fu | M%.1f-%.1f | %ds",
                             pct, minAlt, maxAlt, minMach, maxMach, remainingSeconds());
    }
}