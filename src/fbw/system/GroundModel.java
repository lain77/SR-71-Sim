package fbw.system;

import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class GroundModel {
    private final int vaoId;
    private final List<Integer> vboIds = new ArrayList<>();
    private final int indexCount;

    // Aumentado para 256: vértice a cada ~1.5km (era 4km)
    private static final int VERTEX_COUNT = 256;

    public GroundModel(float size, float uvRepeat) {
        int count = VERTEX_COUNT * VERTEX_COUNT;
        float[] positions = new float[count * 3];
        float[] normals   = new float[count * 3];
        float[] uvs       = new float[count * 2];
        int[]   indices   = new int[6 * (VERTEX_COUNT - 1) * (VERTEX_COUNT - 1)];

        int vertexPointer = 0;
        for (int i = 0; i < VERTEX_COUNT; i++) {
            for (int j = 0; j < VERTEX_COUNT; j++) {
                float x = ((float) j / (VERTEX_COUNT - 1)) * size - (size / 2);
                float z = ((float) i / (VERTEX_COUNT - 1)) * size - (size / 2);
                float y = generateHeight(x, z);

                positions[vertexPointer * 3]     = x;
                positions[vertexPointer * 3 + 1] = y;
                positions[vertexPointer * 3 + 2] = z;

                // Offset proporcional ao espaçamento real entre vértices
                Vector3f normal = calculateNormal(x, z, size / (VERTEX_COUNT - 1));
                normals[vertexPointer * 3]     = normal.x;
                normals[vertexPointer * 3 + 1] = normal.y;
                normals[vertexPointer * 3 + 2] = normal.z;

                uvs[vertexPointer * 2]     = ((float) j / (VERTEX_COUNT - 1)) * uvRepeat;
                uvs[vertexPointer * 2 + 1] = ((float) i / (VERTEX_COUNT - 1)) * uvRepeat;

                // Altura normalizada (0..1) como segundo UV — usada no shader para blending
                float heightNorm = (y + 800f) / 2400f; // range aproximado do terreno
                // Guardamos como atributo extra no UV.y alternativo via segundo canal
                // (veja comentário no shader — usaremos FragPos.y diretamente, mais simples)

                vertexPointer++;
            }
        }

        int pointer = 0;
        for (int gz = 0; gz < VERTEX_COUNT - 1; gz++) {
            for (int gx = 0; gx < VERTEX_COUNT - 1; gx++) {
                int topLeft    = (gz * VERTEX_COUNT) + gx;
                int topRight   = topLeft + 1;
                int bottomLeft = ((gz + 1) * VERTEX_COUNT) + gx;
                int bottomRight = bottomLeft + 1;
                indices[pointer++] = topLeft;
                indices[pointer++] = bottomLeft;
                indices[pointer++] = topRight;
                indices[pointer++] = topRight;
                indices[pointer++] = bottomLeft;
                indices[pointer++] = bottomRight;
            }
        }

        this.indexCount = indices.length;

        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        createVBO(0, 3, positions);
        createVBO(1, 3, normals);
        createVBO(2, 2, uvs);

        IntBuffer indicesBuffer = MemoryUtil.memAllocInt(indices.length);
        indicesBuffer.put(indices).flip();
        int vboId = glGenBuffers();
        vboIds.add(vboId);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, vboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(indicesBuffer);

        glBindVertexArray(0);
    }

    // 4 oitavas de ruído: montanhas grandes + cordilheira + colinas + detalhes
    public static float generateHeight(float x, float z) {
        // Oitava 1 — grandes cadeias (escala aumentada 1800 → 3200)
        float mountains = (float)(
            Math.sin(x * 0.00008 + 1.3) * Math.cos(z * 0.00009 - 0.7) * 0.7 +
            Math.cos(x * 0.00007 - 0.5) * Math.sin(z * 0.00006 + 1.1) * 0.3
        ) * 3200f;

        // Oitava 2 — cordilheiras (800 → 1200)
        float ridges = (float)(
            Math.abs(Math.sin(x * 0.0003 + 0.8) * Math.cos(z * 0.0002 - 1.2))
        ) * 1200f;

        // Oitava 3 — colinas
        float hills = (float)(
            Math.sin(x * 0.001 + 2.1) * Math.cos(z * 0.0012 - 0.4) * 0.5 +
            Math.cos(x * 0.0009 - 1.7) * Math.sin(z * 0.0011 + 0.9) * 0.5
        ) * 300f;

        // Oitava 4 — micro-detalhes
        float detail = (float)(
            Math.sin(x * 0.004 - 0.3) * Math.cos(z * 0.005 + 1.8)
        ) * 60f;

        float combined = mountains + ridges + hills + detail;

        // Planície: tudo abaixo de -400 achata mais
        if (combined < -400f) {
            combined = -400f + (combined + 400f) * 0.1f;
        }

        return combined;
    }

    // Offset proporcional ao espaçamento real — normais precisas
    private Vector3f calculateNormal(float x, float z, float offset) {
        float heightL = generateHeight(x - offset, z);
        float heightR = generateHeight(x + offset, z);
        float heightD = generateHeight(x, z - offset);
        float heightU = generateHeight(x, z + offset);
        return new Vector3f(heightL - heightR, 2f * offset, heightD - heightU).normalize();
    }

    private void createVBO(int index, int size, float[] data) {
        FloatBuffer buffer = MemoryUtil.memAllocFloat(data.length);
        buffer.put(data).flip();
        int vboId = glGenBuffers();
        vboIds.add(vboId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        glEnableVertexAttribArray(index);
        glVertexAttribPointer(index, size, GL_FLOAT, false, 0, 0);
        MemoryUtil.memFree(buffer);
    }

    public void render() {
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDisableVertexAttribArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        for (int vboId : vboIds) glDeleteBuffers(vboId);
        glBindVertexArray(0);
        glDeleteVertexArrays(vaoId);
    }
}