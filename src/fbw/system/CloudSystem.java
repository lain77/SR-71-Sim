package fbw.system;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class CloudSystem {

    // Uma esfera individual dentro de uma nuvem
    private record Sphere(Vector3f position, float size, float darkness) {}

    private final List<Sphere> spheres = new ArrayList<>();
    private final int vaoId;
    private final int vboId;
    private final ShaderProgram shader;

    private static final int   CLOUD_COUNT    = 120;   // era 80
    private static final int   SPHERES_PER    = 12;    // era 10
    private static final float SPREAD         = 300000f; // era 250000

    // Duas camadas de nuvens
    private static final float CUMULUS_MIN    = 7500f;    // 7,500 ft
    private static final float CUMULUS_MAX    = 13500f;   // 13,500 ft
    private static final float CIRRUS_MIN    = 24000f;   // 24,000 ft
    private static final float CIRRUS_MAX    = 33000f;   // 33,000 ft

    public CloudSystem() {
        try {
            String vert = fbw.assets.FileUtils.loadFileAsString("src/res/shaders/cloud_vertex.glsl");
            String frag = fbw.assets.FileUtils.loadFileAsString("src/res/shaders/cloud_fragment.glsl");
            shader = new ShaderProgram(vert, frag);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar shader de nuvens", e);
        }

        // Gera as nuvens aleatoriamente
        Random rng = new Random(42);

     // Cumulus — nuvens grossas baixas
     for (int c = 0; c < CLOUD_COUNT; c++) {
         float cx = (rng.nextFloat() - 0.5f) * SPREAD;
         float cy = CUMULUS_MIN + rng.nextFloat() * (CUMULUS_MAX - CUMULUS_MIN);
         float cz = (rng.nextFloat() - 0.5f) * SPREAD;
         float dark = 0.15f + rng.nextFloat() * 0.45f;

         // Tamanho do cluster varia — algumas nuvens são enormes
         float clusterScale = 0.6f + rng.nextFloat() * 1.4f;

         for (int s = 0; s < SPHERES_PER; s++) {
             float ox = (rng.nextFloat() - 0.5f) * 4000f * clusterScale;
             float oy = (rng.nextFloat() - 0.3f) * 400f;
             float oz = (rng.nextFloat() - 0.5f) * 4000f * clusterScale;
             float sz = (500f + rng.nextFloat() * 1500f) * clusterScale;

             // Base das nuvens mais escura
             float sphereDark = dark + (oy < 0 ? 0.2f : 0f);
             spheres.add(new Sphere(new Vector3f(cx + ox, cy + oy, cz + oz), sz, sphereDark));
         }
     }

     // Cirrus — nuvens finas altas (mais espalhadas)
     for (int c = 0; c < 40; c++) {
         float cx = (rng.nextFloat() - 0.5f) * SPREAD * 2f;
         float cy = CIRRUS_MIN + rng.nextFloat() * (CIRRUS_MAX - CIRRUS_MIN);
         float cz = (rng.nextFloat() - 0.5f) * SPREAD * 2f;

         for (int s = 0; s < 5; s++) {
             float ox = (rng.nextFloat() - 0.5f) * 12000f;
             float oy = (rng.nextFloat() - 0.5f) * 80f;
             float oz = (rng.nextFloat() - 0.5f) * 12000f;
             float sz = 2000f + rng.nextFloat() * 4000f;
             spheres.add(new Sphere(new Vector3f(cx + ox, cy + oy, cz + oz), sz, 0.03f));
         }
     }

        // Quad billboard (mesmo do sol)
        float[] verts = {
            -1f, -1f,
             1f, -1f,
             1f,  1f,
            -1f, -1f,
             1f,  1f,
            -1f,  1f
        };

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        vboId = glGenBuffers();
        FloatBuffer buf = MemoryUtil.memAllocFloat(verts.length);
        buf.put(verts).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        MemoryUtil.memFree(buf);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0);
        glBindVertexArray(0);
    }

    public void render(Camera camera, Vector3f sunDir) {
        Vector3f camPos = camera.getPosition();

        // Ordena de trás para frente (essencial para alpha blending correto)
        spheres.sort(Comparator.comparingDouble(
            s -> -camPos.distanceSquared(s.position())
        ));

        // Extrai vetores right/up da view matrix para o billboard
        Matrix4f viewMat   = camera.getViewMatrix();
        Vector3f camRight  = new Vector3f(viewMat.m00(), viewMat.m10(), viewMat.m20());
        Vector3f camUp     = new Vector3f(viewMat.m01(), viewMat.m11(), viewMat.m21());

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);   // não escreve depth — nuvens são transparentes

        shader.bind();
        shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
        shader.setUniformMatrix4f("view", viewMat);
        shader.setUniform3f("camRight", camRight.x, camRight.y, camRight.z);
        shader.setUniformFloat("camAltitude", camPos.y);
        shader.setUniform3f("camUp",    camUp.x,    camUp.y,    camUp.z);
        shader.setUniform3f("sunDir",   sunDir.x,   sunDir.y,   sunDir.z);

        glBindVertexArray(vaoId);
        for (Sphere s : spheres) {
            // Culling simples: não renderiza nuvens muito longe
            float dist = camPos.distance(s.position());
            if (dist > 200000f) continue;

            // Fade nas bordas do range de visibilidade
            shader.setUniform3f("center",   s.position().x, s.position().y, s.position().z);
            shader.setUniformFloat("size",      s.size());
            shader.setUniformFloat("darkness",  s.darkness());
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }
        glBindVertexArray(0);

        shader.unbind();
        glDepthMask(true);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
        if (shader != null) shader.cleanup();
    }
}