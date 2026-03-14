package fbw.gameplay;

public abstract class Mission {

    public enum State { WAITING, ACTIVE, SUCCESS, FAILED }

    protected State state = State.WAITING;
    protected String name;
    protected String briefing;
    protected float  timeLimit;   // segundos, 0 = sem limite
    protected float  elapsed;

    public abstract void update(float delta, fbw.system.FlyByWire fbw,
                                fbw.system.Enemy enemy);

    // Linha de status exibida no HUD durante a missão
    public abstract String getHudStatus();

    public void start()  { state = State.ACTIVE; elapsed = 0; }
    public State getState()    { return state; }
    public String getName()    { return name; }
    public String getBriefing(){ return briefing; }

    protected void tickTimer(float delta) {
        if (timeLimit <= 0) return;
        elapsed += delta;
        if (elapsed >= timeLimit) state = State.FAILED;
    }

    protected int remainingSeconds() {
        return Math.max(0, (int)(timeLimit - elapsed));
    }
}