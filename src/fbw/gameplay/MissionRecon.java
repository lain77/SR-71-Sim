package fbw.gameplay;

import org.joml.Vector3f;

public class MissionRecon extends Mission {

    private final Vector3f target;
    private final float    minAlt;
    private final float    maxAlt;
    private final float    minSpeed;
    private final float    captureRange;

    private float   captureProgress = 0;
    private static final float REQUIRED = 5f;

    private boolean cloudBlocking = false;

    // Fases da missão
    public enum Phase { APPROACH, HOSTILE_ZONE, OVER_TARGET, CAPTURED, EXTRACT }
    private Phase currentPhase = Phase.APPROACH;
    private float hostileZoneRadius = 25000f;  // raio da zona hostil
    private boolean samLaunched = false;
    private float samTimer = 0;

    public MissionRecon(String name, Vector3f target,
                        float minAlt, float maxAlt, float minSpeed) {
        this.name         = name;
        this.target       = target;
        this.minAlt       = minAlt;
        this.maxAlt       = maxAlt;
        this.minSpeed     = minSpeed;
        this.captureRange = 8000f;
        this.timeLimit    = 300f;  // 5 minutos

        this.briefing = String.format(
            "CLASSIFICADO — SOMENTE LEITURA\n\n" +
            "Inteligencia indica atividade hostil nas coordenadas\n" +
            "X: %.0f  Z: %.0f\n\n" +
            "Sua missao: sobrevoar o alvo e obter imagens\n" +
            "de reconhecimento com a camera ORS.\n\n" +
            "PARAMETROS DE OPERACAO:\n" +
            "  Altitude: %,.0f - %,.0f ft\n" +
            "  Velocidade minima: %.0f m/s\n\n" +
            "ATENCAO: Defesas SAM confirmadas na area.\n" +
            "Mantenha velocidade e altitude para evitar\n" +
            "interceptacao. Boa sorte, Habu.",
            target.x, target.z, minAlt, maxAlt, minSpeed);

        // Mensagens do RSO ao longo da missão
        rsoEvents.add(new RSOEvent(3f,   "Systems nominal. Heading for target area."));
        rsoEvents.add(new RSOEvent(15f,  "INS aligned. Camera system warming up."));
        rsoEvents.add(new RSOEvent(40f,  "Approaching hostile zone. Keep speed up."));
    }

    @Override
    public void start() {
        super.start();
        captureProgress = 0;
        currentPhase    = Phase.APPROACH;
        samLaunched     = false;
        samTimer        = 0;
    }

    @Override
    public void reset() {
        super.reset();
        captureProgress = 0;
        currentPhase    = Phase.APPROACH;
        samLaunched     = false;
        samTimer        = 0;
    }

    @Override
    public void update(float delta, fbw.system.FlyByWire fbw, fbw.system.Enemy enemy) {
        if (state != State.ACTIVE) return;
        tickTimer(delta);

        // Checa vitória PRIMEIRO — antes de qualquer decremento
        if (captureProgress >= REQUIRED) {
            state = State.SUCCESS;
            return;
        }

        fbw.system.FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        Vector3f pos = fbw.getPosicao();
        float dx = pos.x - target.x;
        float dz = pos.z - target.z;
        float horizDist = (float) Math.sqrt(dx * dx + dz * dz);
        this.distToTarget = horizDist;

        // Atualiza fase...
        Phase prevPhase = currentPhase;
        
        if (horizDist < captureRange) {
            currentPhase = Phase.OVER_TARGET;
        } else if (horizDist < hostileZoneRadius) {
            currentPhase = Phase.HOSTILE_ZONE;
        } else if (captureProgress >= REQUIRED) {
            currentPhase = Phase.EXTRACT;
        } else {
            currentPhase = Phase.APPROACH;
        }

        // SAMs na zona hostil
        if (currentPhase == Phase.HOSTILE_ZONE || currentPhase == Phase.OVER_TARGET) {
            samTimer += delta;
            if (samTimer > 12f && !samLaunched) {
                enemy.launchMissile();
                samLaunched = true;
            }
            if (samTimer > 18f) {
                samTimer = 0;
                samLaunched = false;
            }
        }

        // RSO reativo
        if (prevPhase != currentPhase) {
            switch (currentPhase) {
                case HOSTILE_ZONE -> rsoEvents.add(new RSOEvent(elapsed + 0.1f, "HOSTILE ZONE. SAM radar painting us!"));
                case OVER_TARGET  -> rsoEvents.add(new RSOEvent(elapsed + 0.1f, "OVER TARGET! Open camera — F key!"));
            }
        }

        // Progresso de captura (sem altitude)
        float spd = (float) data.getSpeed();
        boolean overTarget = horizDist < captureRange;
        boolean speedOk = spd >= minSpeed;

        if (overTarget && speedOk && !cloudBlocking) {
            captureProgress += delta;
        } else {
            captureProgress = Math.max(0, captureProgress - delta * 0.5f);
        }
    }

    public void setCloudBlocking(boolean b) { cloudBlocking = b; }

    @Override
    public String getHudStatus() {
        if (state == State.SUCCESS) return "RECON COMPLETE — RTB";
        if (state == State.FAILED)  return "MISSION FAILED — TIME EXPIRED";

        float pct = (captureProgress / REQUIRED) * 100f;

        return switch (currentPhase) {
            case APPROACH     -> String.format("APPROACH | TGT: %.0fkm | %ds",
                                    getDistToTarget() / 1000f, remainingSeconds());
            case HOSTILE_ZONE -> String.format("!! SAM ZONE !! | TGT: %.0fkm | %ds",
                                    getDistToTarget() / 1000f, remainingSeconds());
            case OVER_TARGET  -> String.format("OVER TARGET | CAPTURE: %2.0f%% | %ds",
                                    pct, remainingSeconds());
            case CAPTURED     -> "CAPTURED — EXFILTRATE NOW";
            case EXTRACT      -> "EGRESS — GET CLEAR OF SAM ZONE";
        };
    }

    private float distToTarget = 0;
    private float getDistToTarget() { return distToTarget; }

    public void capturePhoto() {
        captureProgress = REQUIRED;
    }

    public Phase   getPhase()   { return currentPhase; }
    public Vector3f getTarget() { return target; }
    public float   getMinAlt()  { return minAlt; }
    public float   getMaxAlt()  { return maxAlt; }
    public float getCaptureProgress() { return captureProgress; }
    public float getRequired()        { return REQUIRED; }
    public float getMinSpeed()        { return minSpeed; }
}