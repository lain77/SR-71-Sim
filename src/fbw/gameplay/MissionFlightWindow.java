package fbw.gameplay;

public class MissionFlightWindow extends Mission {

    private final float minAlt, maxAlt;
    private final float minMach, maxMach;
    private final float requiredTime;

    private float timeInWindow = 0;

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
        this.briefing = String.format(
            "CHECKRIDE — AVALIACAO DE PILOTO\n\n" +
            "Demonstre controle do SR-71 mantendo\n" +
            "a aeronave dentro dos parametros:\n\n" +
            "  Altitude: %,.0f - %,.0f ft\n" +
            "  Velocidade: Mach %.1f - %.1f\n" +
            "  Duracao: %.0f segundos\n\n" +
            "Boa sorte, piloto.",
            minAlt, maxAlt, minMach, maxMach, requiredTime);

        rsoEvents.add(new RSOEvent(2f,  "Checkride starting. Get into the window."));
        rsoEvents.add(new RSOEvent(15f, "Looking good — hold it steady."));
    }

    @Override
    public void start() {
        super.start();
        timeInWindow = 0;
    }

    @Override
    public void reset() {
        super.reset();
        timeInWindow = 0;
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
            if (timeInWindow >= requiredTime) state = State.SUCCESS;
        } else {
            timeInWindow = Math.max(0, timeInWindow - delta);
        }
    }

    @Override
    public String getHudStatus() {
        if (state == State.SUCCESS) return "CHECKRIDE PASSED";
        if (state == State.FAILED)  return "CHECKRIDE FAILED — TIME UP";

        float pct = (timeInWindow / requiredTime) * 100f;
        return String.format("CHECKRIDE | %2.0f%% | ALT:%,.0f-%,.0f | M%.1f-%.1f | %ds",
                             pct, minAlt, maxAlt, minMach, maxMach, remainingSeconds());
    }
    
    public float getMinAlt()  { return minAlt; }
    public float getMaxAlt()  { return maxAlt; }
    public float getMinMach() { return minMach; }
    public float getMaxMach() { return maxMach; }
}