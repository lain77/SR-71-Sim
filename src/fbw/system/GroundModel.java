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

                Vector3f normal = calculateNormal(x, z, size / (VERTEX_COUNT - 1));
                normals[vertexPointer * 3]     = normal.x;
                normals[vertexPointer * 3 + 1] = normal.y;
                normals[vertexPointer * 3 + 2] = normal.z;

                uvs[vertexPointer * 2]     = ((float) j / (VERTEX_COUNT - 1)) * uvRepeat;
                uvs[vertexPointer * 2 + 1] = ((float) i / (VERTEX_COUNT - 1)) * uvRepeat;

                vertexPointer++;
            }
        }

        int pointer = 0;
        for (int gz = 0; gz < VERTEX_COUNT - 1; gz++) {
            for (int gx = 0; gx < VERTEX_COUNT - 1; gx++) {
                int topLeft     = (gz * VERTEX_COUNT) + gx;
                int topRight    = topLeft + 1;
                int bottomLeft  = ((gz + 1) * VERTEX_COUNT) + gx;
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

    // ── RUÍDO BASE (substitui Math.random — determinístico e suave) ──
    private static float hash(float x, float z) {
        // Hash sem tabela — combinação de senos com números irracionais
        double val = Math.sin(x * 127.1 + z * 311.7) * 43758.5453;
        return (float)(val - Math.floor(val));
    }

    // Interpolação suave (smoothstep cúbico)
    private static float fade(float t) {
        return t * t * (3f - 2f * t);
    }

    // Ruído de valor 2D suave
    private static float valueNoise(float x, float z) {
        float ix = (float) Math.floor(x);
        float iz = (float) Math.floor(z);
        float fx = x - ix;
        float fz = z - iz;

        float ux = fade(fx);
        float uz = fade(fz);

        float a = hash(ix,     iz);
        float b = hash(ix + 1, iz);
        float c = hash(ix,     iz + 1);
        float d = hash(ix + 1, iz + 1);

        return a + (b - a) * ux
             + (c - a) * uz
             + (a - b - c + d) * ux * uz;
    }

    // fBm — Fractal Brownian Motion com N oitavas
    private static float fbm(float x, float z, int octaves,
                              float frequency, float lacunarity, float gain) {
        float value     = 0f;
        float amplitude = 1f;
        float freq      = frequency;
        float maxVal    = 0f;

        for (int i = 0; i < octaves; i++) {
            value   += valueNoise(x * freq, z * freq) * amplitude;
            maxVal  += amplitude;
            amplitude *= gain;
            freq      *= lacunarity;
        }
        return value / maxVal; // normaliza 0..1
    }

    // Ridge noise — cria picos pontiagudos tipo Cáucaso
    private static float ridgeFbm(float x, float z, int octaves,
                                   float frequency, float lacunarity, float gain) {
        float value     = 0f;
        float amplitude = 1f;
        float freq      = frequency;
        float maxVal    = 0f;
        float prev      = 1f;

        for (int i = 0; i < octaves; i++) {
            float n = valueNoise(x * freq, z * freq);
            n = 1f - Math.abs(n * 2f - 1f); // inverte vales em picos
            n = n * n;                        // aguça os picos
            value   += n * amplitude * prev;
            prev     = n;
            maxVal  += amplitude;
            amplitude *= gain;
            freq      *= lacunarity;
        }
        return value / maxVal;
    }

    // ── GERADOR PRINCIPAL ─────────────────────────────────────────────
    public static float generateHeight(float x, float z) {
        float nx = x * 0.000045f;
        float nz = z * 0.000045f;

        float regionMask = fbm(nx * 0.25f, nz * 0.25f, 3, 1.0f, 2.0f, 0.5f);

        float ridgeAngle = (x * 0.6f + z * 1.0f) * 0.000025f;
        float ridgeMask  = (float) Math.exp(-ridgeAngle * ridgeAngle * 0.5f);
        float ridge      = ridgeFbm(nx, nz, 6, 1.0f, 2.1f, 0.5f);

        float plains = fbm(nx, nz, 4, 1.0f, 2.0f, 0.5f);
        float detail = fbm(nx * 8f, nz * 8f, 3, 1.0f, 2.0f, 0.45f) * 0.08f;

        float mountainBlend = smoothstep(0.35f, 0.65f, regionMask);
        float plainHeight   = (plains - 0.4f) * 600f;

        // 3x mais alto que antes
        float mountainHeight = ridge * (8400f + regionMask * 3600f);
        mountainHeight *= (0.4f + ridgeMask * 0.6f);

        float combined = mix(plainHeight, mountainHeight, mountainBlend);
        combined += detail * 200f;

        // Rios: vales estreitos onde o fBm tem valor baixo específico
        float riverNoise = fbm(nx * 2f, nz * 2f, 2, 1.0f, 2.0f, 0.5f);
        float riverMask  = smoothstep(0.48f, 0.50f, riverNoise)
                         * smoothstep(0.52f, 0.50f, riverNoise); // faixa estreita
        riverMask *= (1f - mountainBlend); // só em planícies
        combined  -= riverMask * 400f; // escava o vale do rio

        // Lagos: depressões em áreas planas com mask baixo
        float lakeMask = smoothstep(0.30f, 0.25f, regionMask)
                       * smoothstep(0.3f, 0.0f, fbm(nx * 3f, nz * 3f, 2, 1.0f, 2.0f, 0.5f));
        combined -= lakeMask * 200f;

        float edgeDist = edgeDistance(x, z, 200000f);
        if (edgeDist < 1.0f) {
            combined = mix(-80f, combined, edgeDist * edgeDist);
        }

        if (combined < -60f) {
            combined = -60f + (combined + 60f) * 0.15f;
        }

        return combined;
    }

    // Utilitários
    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // Distância normalizada até a borda do mapa (0=borda, 1=centro)
    private static float edgeDistance(float x, float z, float halfSize) {
        float dx = Math.max(0f, Math.abs(x) - halfSize * 0.85f);
        float dz = Math.max(0f, Math.abs(z) - halfSize * 0.85f);
        float d  = (float) Math.sqrt(dx * dx + dz * dz);
        return Math.max(0f, 1f - d / (halfSize * 0.15f));
    }

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