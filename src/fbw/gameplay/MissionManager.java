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
        // Missão 1 — introdução: manter janela de voo
        missions.add(new MissionFlightWindow(
            "Missao 1: Voar Alto",
            20000f, 30000f,   // altitude
            1.5f,   3.0f,     // Mach
            20f               // 20 segundos
        ));

        // Missão 2 — reconhecimento no centro do mapa
        missions.add(new MissionRecon(
            "Missao 2: Operacao Oxcart",
            new Vector3f(0f, 0f, 0f),
            15000f, 28000f,   // altitude
            250f              // ~Mach 0.7 mínimo
        ));

        // Missão 3 — fuga de interceptação
        missions.add(new MissionEscape(
            "Missao 3: Haast's Eagle",
            2.5f  // Mach 2.5
        ));

        // Missão 4 — recon avançado com janela apertada
        missions.add(new MissionRecon(
            "Missao 4: Deep Penetration",
            new Vector3f(50000f, 0f, -30000f),
            22000f, 26000f,
            400f
        ));

        // Missão 5 — fuga hipersônica final
        missions.add(new MissionEscape(
            "Missao 5: Mach 3 ou morte",
            3.0f
        ));
    }

    // Chamado a cada frame com delta time em segundos
    public void update(float delta, fbw.system.FlyByWire fbw, fbw.system.Enemy enemy) {
        if (menuOpen || currentIndex < 0) return;
        Mission m = currentMission();
        if (m == null) return;

        m.update(delta, fbw, enemy);

        // Auto-avança para próxima após sucesso
        if (m.getState() == Mission.State.SUCCESS) {
            if (currentIndex + 1 < missions.size()) {
                currentIndex++;
                missions.get(currentIndex).start();
            }
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