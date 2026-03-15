package fbw.system;

import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class PostProcessQuad {
    private final int vaoId, vboId;

    public PostProcessQuad() {
        float[] verts = {
            // pos      // uv
            -1f,  1f,   0f, 1f,
            -1f, -1f,   0f, 0f,
             1f, -1f,   1f, 0f,
            -1f,  1f,   0f, 1f,
             1f, -1f,   1f, 0f,
             1f,  1f,   1f, 1f
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
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 16, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 16, 8);
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