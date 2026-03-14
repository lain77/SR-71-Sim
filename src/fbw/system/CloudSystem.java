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

    private static final int   CLOUD_COUNT    = 60;   // número de nuvens
    private static final int   SPHERES_PER    = 8;    // esferas por nuvem
    private static final float SPREAD         = 180000f; // área de distribuição
    private static final float ALT_MIN        = 2000f;
    private static final float ALT_MAX        = 4000f;

    public CloudSystem() {
        try {
            String vert = fbw.assets.FileUtils.loadFileAsString("src/res/shaders/cloud_vertex.glsl");
            String frag = fbw.assets.FileUtils.loadFileAsString("src/res/shaders/cloud_fragment.glsl");
            shader = new ShaderProgram(vert, frag);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar shader de nuvens", e);
        }

        // Gera as nuvens aleatoriamente
        Random rng = new Random(42); // seed fixo = mesmas nuvens sempre
        for (int c = 0; c < CLOUD_COUNT; c++) {
            // Centro do cluster
            float cx = (rng.nextFloat() - 0.5f) * SPREAD;
            float cy = ALT_MIN + rng.nextFloat() * (ALT_MAX - ALT_MIN);
            float cz = (rng.nextFloat() - 0.5f) * SPREAD;

            // Escuridão da base (nuvens mais altas ficam mais escuras embaixo)
            float dark = rng.nextFloat() * 0.5f;

            for (int s = 0; s < SPHERES_PER; s++) {
                // Espalha esferas ao redor do centro do cluster
                float ox = (rng.nextFloat() - 0.5f) * 3000f;
                float oy = (rng.nextFloat() - 0.5f) * 600f;
                float oz = (rng.nextFloat() - 0.5f) * 3000f;
                float sz = 800f + rng.nextFloat() * 1400f; // tamanho variado

                spheres.add(new Sphere(new Vector3f(cx + ox, cy + oy, cz + oz), sz, dark));
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
        shader.setUniform3f("camUp",    camUp.x,    camUp.y,    camUp.z);
        shader.setUniform3f("sunDir",   sunDir.x,   sunDir.y,   sunDir.z);

        glBindVertexArray(vaoId);
        for (Sphere s : spheres) {
            // Culling simples: não renderiza nuvens muito longe
            float dist = camPos.distance(s.position());
            if (dist > 120000f) continue;

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