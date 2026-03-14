package fbw.gameplay;

import org.joml.Vector3f;

public class MissionRecon extends Mission {

    private final Vector3f target;       // posição do alvo no mundo
    private final float    minAlt;       // altitude mínima para foto (não muito baixo)
    private final float    maxAlt;       // altitude máxima (não muito alto)
    private final float    minSpeed;     // velocidade mínima m/s
    private final float    captureRange; // raio horizontal para "estar sobre o alvo"

    private float captureProgress = 0;   // segundos voando na janela correta
    private static final float REQUIRED  = 5f; // precisa ficar 5s na janela

    private boolean cloudBlocking = false;

    public MissionRecon(String name, Vector3f target,
                        float minAlt, float maxAlt, float minSpeed) {
        this.name         = name;
        this.target       = target;
        this.minAlt       = minAlt;
        this.maxAlt       = maxAlt;
        this.minSpeed     = minSpeed;
        this.captureRange = 8000f;
        this.timeLimit    = 180f; // 3 minutos
        this.briefing     = String.format(
            "Fotografe o alvo em %.0f-%,.0f u de altitude\n" +
            "Velocidade mínima: %.0f m/s\nAlvo: X=%.0f Z=%.0f",
            minAlt, maxAlt, minSpeed, target.x, target.z);
    }

    @Override
    public void update(float delta, fbw.system.FlyByWire fbw, fbw.system.Enemy enemy) {
        if (state != State.ACTIVE) return;
        tickTimer(delta);

        fbw.system.FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        Vector3f pos  = fbw.getPosicao();
        float    alt  = (float) data.getAltitude();
        float    spd  = (float) data.getSpeed();

        float dx = pos.x - target.x;
        float dz = pos.z - target.z;
        float horizDist = (float) Math.sqrt(dx*dx + dz*dz);

        boolean overTarget  = horizDist < captureRange;
        boolean altOk       = alt >= minAlt && alt <= maxAlt;
        boolean speedOk     = spd >= minSpeed;

        if (overTarget && altOk && speedOk && !cloudBlocking) {
            captureProgress += delta;
            if (captureProgress >= REQUIRED) state = State.SUCCESS;
        } else {
            // Perde progresso se sair da janela
            captureProgress = Math.max(0, captureProgress - delta * 0.5f);
        }
    }

    public void setCloudBlocking(boolean b) { cloudBlocking = b; }

    @Override
    public String getHudStatus() {
        if (state == State.SUCCESS) return "FOTO CAPTURADA!";
        if (state == State.FAILED)  return "MISSAO FALHOU - Tempo esgotado";

        float pct = (captureProgress / REQUIRED) * 100f;
        return String.format("RECON | Progresso: %2.0f%% | Tempo: %ds",
                             pct, remainingSeconds());
    }
}