package fbw.system;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class SkyRenderer {

    private final int vaoId;
    private final int vboId;
    private final ShaderProgram shader;

    public SkyRenderer() {
        try {
            String vert = fbw.assets.FileUtils.loadFileAsString("src/res/shaders/sky_vertex.glsl");
            String frag = fbw.assets.FileUtils.loadFileAsString("src/res/shaders/sky_fragment.glsl");
            shader = new ShaderProgram(vert, frag);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao carregar sky shader", e);
        }

        float[] verts = { -1,-1, 1,-1, 1,1, -1,-1, 1,1, -1,1 };
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

    public void render(Camera camera, Vector3f sunDir, float altitude) {
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);

        Matrix4f invProj = camera.getProjectionMatrix().invert(new Matrix4f());
        Matrix4f invView = camera.getViewMatrix().invert(new Matrix4f());
        invView.m30(0); invView.m31(0); invView.m32(0);

        shader.bind();
        shader.setUniformMatrix4f("invProj", invProj);
        shader.setUniformMatrix4f("invView", invView);
        shader.setUniform3f("sunDir", sunDir.x, sunDir.y, sunDir.z);
        shader.setUniform3f("camPos",
            camera.getPosition().x,
            camera.getPosition().y,
            camera.getPosition().z);
        shader.setUniformFloat("altitude", altitude);

        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);

        shader.unbind();
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
    }

    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
        if (shader != null) shader.cleanup();
    }
}