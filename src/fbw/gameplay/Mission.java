package fbw.gameplay;

import java.util.ArrayList;
import java.util.List;

public abstract class Mission {

    public enum State { WAITING, ACTIVE, SUCCESS, FAILED }

    protected State  state = State.WAITING;
    protected String name;
    protected String briefing;
    protected float  timeLimit;
    protected float  elapsed;

    // Sistema de mensagens RSO
    protected List<RSOEvent> rsoEvents = new ArrayList<>();
    protected int nextRsoEvent = 0;

    public abstract void update(float delta, fbw.system.FlyByWire fbw,
                                fbw.system.Enemy enemy);
    public abstract String getHudStatus();

    public void start() {
        state   = State.ACTIVE;
        elapsed = 0;
        nextRsoEvent = 0;
    }

    public void reset() {
        state   = State.WAITING;
        elapsed = 0;
        nextRsoEvent = 0;
    }

    public State   getState()    { return state; }
    public String  getName()     { return name; }
    public String  getBriefing() { return briefing; }

    protected void tickTimer(float delta) {
        if (timeLimit <= 0) return;
        elapsed += delta;
        if (elapsed >= timeLimit) state = State.FAILED;
    }

    protected int remainingSeconds() {
        return Math.max(0, (int)(timeLimit - elapsed));
    }

    // Checa se tem mensagem RSO pra disparar
    public String checkRSO() {
        if (nextRsoEvent >= rsoEvents.size()) return null;
        RSOEvent evt = rsoEvents.get(nextRsoEvent);
        if (elapsed >= evt.triggerTime) {
            nextRsoEvent++;
            return evt.message;
        }
        return null;
    }

    // Classe interna pra eventos RSO
    protected static class RSOEvent {
        float  triggerTime;  // segundos após início
        String message;

        RSOEvent(float time, String msg) {
            this.triggerTime = time;
            this.message     = msg;
        }
    }
}