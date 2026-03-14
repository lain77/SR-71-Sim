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

    // Resolução do terreno (100x100 = 10.000 vértices)
    private static final int VERTEX_COUNT = 100;

    public GroundModel(float size, float uvRepeat) {
        int count = VERTEX_COUNT * VERTEX_COUNT;
        float[] positions = new float[count * 3];
        float[] normals = new float[count * 3];
        float[] uvs = new float[count * 2];
        int[] indices = new int[6 * (VERTEX_COUNT - 1) * (VERTEX_COUNT - 1)];

        int vertexPointer = 0;
        for (int i = 0; i < VERTEX_COUNT; i++) {
            for (int j = 0; j < VERTEX_COUNT; j++) {
                // Mapeia o índice do grid para a posição real no mundo 3D
                float x = ((float) j / (VERTEX_COUNT - 1)) * size - (size / 2);
                float z = ((float) i / (VERTEX_COUNT - 1)) * size - (size / 2);

                // --- GERAÇÃO DE ALTURA (Montanhas e Vales) ---
                float y = generateHeight(x, z);

                positions[vertexPointer * 3] = x;
                positions[vertexPointer * 3 + 1] = y;
                positions[vertexPointer * 3 + 2] = z;

                // --- CÁLCULO DE LUZ (Normais) ---
                Vector3f normal = calculateNormal(x, z);
                normals[vertexPointer * 3] = normal.x;
                normals[vertexPointer * 3 + 1] = normal.y;
                normals[vertexPointer * 3 + 2] = normal.z;

                uvs[vertexPointer * 2] = ((float) j / (VERTEX_COUNT - 1)) * uvRepeat;
                uvs[vertexPointer * 2 + 1] = ((float) i / (VERTEX_COUNT - 1)) * uvRepeat;
                
                vertexPointer++;
            }
        }

        int pointer = 0;
        for (int gz = 0; gz < VERTEX_COUNT - 1; gz++) {
            for (int gx = 0; gx < VERTEX_COUNT - 1; gx++) {
                int topLeft = (gz * VERTEX_COUNT) + gx;
                int topRight = topLeft + 1;
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

        // --- Alocação de Memória na GPU ---
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

    // Função matemática que cria as montanhas
    private float generateHeight(float x, float z) {
        // Mistura de ondas grandes (montanhas) e ondas pequenas (colinas)
        float montanhas = (float) (Math.sin(x * 0.0005) * Math.cos(z * 0.0005)) * 600f;
        float colinas = (float) (Math.sin(x * 0.002) * Math.cos(z * 0.001)) * 100f;
        return montanhas + colinas - 200f; // Empurra um pouco para baixo
    }

    // Descobre para onde a montanha está "apontando" para o Shader iluminar
    private Vector3f calculateNormal(float x, float z) {
        float offset = 10f;
        float heightL = generateHeight(x - offset, z);
        float heightR = generateHeight(x + offset, z);
        float heightD = generateHeight(x, z - offset);
        float heightU = generateHeight(x, z + offset);
        
        Vector3f normal = new Vector3f(heightL - heightR, 2f * offset, heightD - heightU);
        return normal.normalize();
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