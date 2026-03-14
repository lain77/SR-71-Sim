package fbw.system;

import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class SunBillboard {
    private final int vaoId;
    private final int vboId;

    public SunBillboard() {
        // Quad simples de 2 triângulos
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

    public void render() {
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, 6);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteVertexArrays(vaoId);
    }
}