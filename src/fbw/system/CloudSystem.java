package fbw.system;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImage.*;

public class CloudSystem {

    private record Sphere(Vector3f position, float size, float darkness, int texIndex) {}

    private final List<Sphere> spheres = new ArrayList<>();
    private final int vaoId;
    private final int vboId;
    private final ShaderProgram shader;
    private final int[] cloudTextures = new int[2];

    private static final int   CLOUD_COUNT = 200; 
    private static final int   SPHERES_PER = 12;     
    private static final float SPREAD      = 400000f; 

    private static final float CUMULUS_MIN  = 12000f; 
    private static final float CUMULUS_MAX  = 22000f;  
    private static final float CIRRUS_MIN  = 30000f;  
    private static final float CIRRUS_MAX  = 42000f;  

    public CloudSystem() {
        try {
            String vert = fbw.assets.FileUtils.loadFileAsString("src/res/shaders/cloud_vertex.glsl");
            String frag = fbw.assets.FileUtils.loadFileAsString("src/res/shaders/cloud_fragment.glsl");
            shader = new ShaderProgram(vert, frag);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar shader de nuvens", e);
        }

        // Load cloud noise textures
        cloudTextures[0] = loadTexture("src/img/cloud_noise.png");
        cloudTextures[1] = loadTexture("src/img/cloud_noise2.png");

        Random rng = new Random(42);

     // Cumulus — thick low clouds
        for (int c = 0; c < CLOUD_COUNT; c++) {
            float cx = (rng.nextFloat() - 0.5f) * SPREAD;
            float cy = CUMULUS_MIN + rng.nextFloat() * (CUMULUS_MAX - CUMULUS_MIN);
            float cz = (rng.nextFloat() - 0.5f) * SPREAD;
            float dark = 0.15f + rng.nextFloat() * 0.45f;
            float clusterScale = 0.8f + rng.nextFloat() * 1.6f;  // era 0.6 + 1.4
            int texIdx = rng.nextInt(2);

            for (int s = 0; s < SPHERES_PER; s++) {
                float ox = (rng.nextFloat() - 0.5f) * 6000f * clusterScale;  // era 4000
                float oy = (rng.nextFloat() - 0.3f) * 400f;   // era 300
                float oz = (rng.nextFloat() - 0.5f) * 6000f * clusterScale;  // era 4000
                float sz = (1200f + rng.nextFloat() * 3000f) * clusterScale;  // era 800+2000

                float sphereDark = dark + (oy < 0 ? 0.25f : 0f);
                spheres.add(new Sphere(
                    new Vector3f(cx + ox, cy + oy, cz + oz),
                    sz, sphereDark, texIdx));
            }
        }

     // Cirrus — thin high clouds
        for (int c = 0; c < 60; c++) {  // era 40
            float cx = (rng.nextFloat() - 0.5f) * SPREAD * 2.5f;  // era 2.0
            float cy = CIRRUS_MIN + rng.nextFloat() * (CIRRUS_MAX - CIRRUS_MIN);
            float cz = (rng.nextFloat() - 0.5f) * SPREAD * 2.5f;
            int texIdx = rng.nextInt(2);

            for (int s = 0; s < 6; s++) {  // era 5
                float ox = (rng.nextFloat() - 0.5f) * 18000f;  // era 12000
                float oy = (rng.nextFloat() - 0.5f) * 80f;
                float oz = (rng.nextFloat() - 0.5f) * 18000f;
                float sz = 4000f + rng.nextFloat() * 8000f;  // era 2500+5000
                spheres.add(new Sphere(
                    new Vector3f(cx + ox, cy + oy, cz + oz),
                    sz, 0.03f, texIdx));
            }
        }

        // Quad billboard
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

    private int loadTexture(String path) {
        IntBuffer w = BufferUtils.createIntBuffer(1);
        IntBuffer h = BufferUtils.createIntBuffer(1);
        IntBuffer c = BufferUtils.createIntBuffer(1);

        stbi_set_flip_vertically_on_load(false);
        ByteBuffer image = stbi_load(path, w, h, c, 4);
        if (image == null) {
            System.err.println("Failed to load cloud texture: " + path);
            return 0;
        }

        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0), 0,
                     GL_RGBA, GL_UNSIGNED_BYTE, image);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glGenerateMipmap(GL_TEXTURE_2D);
        stbi_image_free(image);
        glBindTexture(GL_TEXTURE_2D, 0);

        System.out.println("Cloud texture loaded: " + path + " (" + w.get(0) + "x" + h.get(0) + ")");
        return texId;
    }

    public void render(Camera camera, Vector3f sunDir) {
        Vector3f camPos = camera.getPosition();

        // Sort back to front
        spheres.sort(Comparator.comparingDouble(
            s -> -camPos.distanceSquared(s.position())
        ));

        Matrix4f viewMat  = camera.getViewMatrix();
        Vector3f camRight = new Vector3f(viewMat.m00(), viewMat.m10(), viewMat.m20());
        Vector3f camUp    = new Vector3f(viewMat.m01(), viewMat.m11(), viewMat.m21());

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);

        shader.bind();
        shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
        shader.setUniformMatrix4f("view", viewMat);
        shader.setUniform3f("camRight", camRight.x, camRight.y, camRight.z);
        shader.setUniform3f("camUp",    camUp.x,    camUp.y,    camUp.z);
        shader.setUniform3f("sunDir",   sunDir.x,   sunDir.y,   sunDir.z);
        shader.setUniformFloat("camAltitude", camPos.y);
        shader.setUniform1i("cloudTex", 0);

        glBindVertexArray(vaoId);

        int lastTexIdx = -1;
        for (Sphere s : spheres) {
            float dist = camPos.distance(s.position());
            if (dist > 200000f) continue;

            // Bind texture only when it changes
            if (s.texIndex() != lastTexIdx) {
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, cloudTextures[s.texIndex()]);
                lastTexIdx = s.texIndex();
            }

            shader.setUniform3f("center", s.position().x, s.position().y, s.position().z);
            shader.setUniformFloat("size",     s.size());
            shader.setUniformFloat("darkness", s.darkness());
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }

        glBindVertexArray(0);
        glBindTexture(GL_TEXTURE_2D, 0);

        shader.unbind();
        glDepthMask(true);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
        for (int tex : cloudTextures) {
            if (tex > 0) glDeleteTextures(tex);
        }
        if (shader != null) shader.cleanup();
    }
}