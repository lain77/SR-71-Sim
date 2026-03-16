package fbw.gameplay;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

public class MissionManager {

    private final List<Mission> missions = new ArrayList<>();
    private int     currentIndex  = -1; // -1 = nenhuma ativa (no menu)
    private boolean menuOpen      = true;

    public MissionManager() {
        buildCampaign();
    }

    private void buildCampaign() {
        // Missão 1 — tutorial (altitude em ft real do jogo)
        missions.add(new MissionFlightWindow(
            "CHECKRIDE",
            15000f, 40000f,    // range bem aberto
            1.0f, 3.5f,        // Mach range aberto
            15f                 // 15 segundos
        ));

        // Missão 2 — primeiro recon
        missions.add(new MissionRecon(
            "GIANT REACH",
            new Vector3f(0f, 0f, 0f),
            0f, 999999f,       // sem restrição de altitude por ora
            200f               // velocidade mínima baixa
        ));

        // Missão 3 — fuga
        missions.add(new MissionEscape(
            "HAAST'S EAGLE",
            2.0f               // Mach 2 — mais acessível
        ));

        // Missão 4 — recon avançado
        missions.add(new MissionRecon(
            "DEEP PENETRATION",
            new Vector3f(50000f, 0f, -30000f),
            0f, 999999f,
            300f
        ));

        // Missão 5 — final
        missions.add(new MissionEscape(
            "SENIOR CROWN",
            2.8f
        ));
    }

    public void update(float delta, fbw.system.FlyByWire fbw, fbw.system.Enemy enemy) {
        if (menuOpen || currentIndex < 0) return;
        Mission m = currentMission();
        if (m == null) return;
        
        if (m.getState() == Mission.State.ACTIVE) {
            m.update(delta, fbw, enemy);
        }
    }

    public void selectMission(int index) {
        if (index < 0 || index >= missions.size()) return;
        currentIndex = index;
        menuOpen     = false;
        missions.get(index).start();
    }

    public void openMenu() { menuOpen = true; }

    public Mission currentMission() {
        if (currentIndex < 0 || currentIndex >= missions.size()) return null;
        return missions.get(currentIndex);
    }

    public List<Mission> getMissions() { return missions; }
    public boolean       isMenuOpen()  { return menuOpen; }
    public int           getCurrentIndex() { return currentIndex; }
}