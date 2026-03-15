package fbw.system;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.assimp.AIScene;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;

public class TreeSystem {

    private final List<Vector3f> positions = new ArrayList<>();
    private final List<Float>    scales    = new ArrayList<>();
    private final List<Model>    models    = new ArrayList<>();
    private final AIScene        scene;

    private static final int   TREE_COUNT = 1500;
    private static final float SPREAD     = 80000f;
    private static final float MAX_ALT    = 800f;
    private static final float DRAW_DIST  = 20000f;

    public TreeSystem() {
        // Carrega o modelo GLB
        scene = Assimp.aiImportFile(
            "src/models/assets/tree_elm.glb",
            Assimp.aiProcess_Triangulate |
            Assimp.aiProcess_FlipUVs     |
            Assimp.aiProcess_GenNormals
        );

        if (scene == null || scene.mRootNode() == null)
            throw new RuntimeException("Erro ao carregar árvore: " + Assimp.aiGetErrorString());

        models.addAll(Model.loadAllFromScene(scene));
        System.out.println(" Modelos de árvore carregados: " + models.size());

        // Gera posições aleatórias em terreno baixo
        Random rng = new Random(77);
        int attempts = 0;
        while (positions.size() < TREE_COUNT && attempts < TREE_COUNT * 10) {
            attempts++;
            float x = (rng.nextFloat() - 0.5f) * SPREAD * 2;
            float z = (rng.nextFloat() - 0.5f) * SPREAD * 2;
            float y = GroundModel.generateHeight(x, z);
            
            if (y > 50f && y < MAX_ALT) { // era > -50, agora > 50 para evitar água
                positions.add(new Vector3f(x, y, z));
                scales.add(25f + rng.nextFloat() * 15f);
            }
        }
        System.out.println(" Árvores posicionadas: " + positions.size());
    }

    public void render(Camera camera, ShaderProgram shader) {
        Vector3f camPos = camera.getPosition();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        for (int i = 0; i < positions.size(); i++) {
            Vector3f pos  = positions.get(i);
            float    dist = camPos.distance(pos);
            if (dist > DRAW_DIST) continue;

            Matrix4f model = new Matrix4f()
                .translate(pos)
                .scale(scales.get(i));

            shader.setUniformMatrix4f("model", model);
            shader.setUniform1i("useTexture", 0); // modelo GLB gerencia sua própria textura
            shader.setUniform1i("useTerrain", 0);
            shader.setUniform3f("objectColor", 0.18f, 0.38f, 0.12f);

            for (Model m : models) m.render();
        }
    }

    public void cleanup() {
        for (Model m : models) m.cleanup();
        if (scene != null) Assimp.aiReleaseImport(scene);
    }
}