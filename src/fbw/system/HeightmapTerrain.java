package fbw.system;

import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class HeightmapTerrain {

    private final int vaoId;
    private final List<Integer> vboIds = new ArrayList<>();
    private final int indexCount;

    // Terrain dimensions in game units (feet)
    private final float terrainWidth;
    private final float terrainDepth;
    private final float maxHeight;

    // Heightmap data for collision detection
    private final float[][] heightData;
    private final int gridW;
    private final int gridH;

    // Grid resolution for mesh (not image resolution)
    private static final int MESH_RES_X = 512;
    private static final int MESH_RES_Z = 256;

    /**
     * @param heightmapPath  path to grayscale PNG heightmap
     * @param width          terrain width in game units
     * @param depth          terrain depth in game units
     * @param maxHeight      maximum height (brightest pixel = this height)
     * @param seaThreshold   pixel values below this are treated as sea (height 0 or below)
     */
    public HeightmapTerrain(String heightmapPath, float width, float depth,
                            float maxHeight, int seaThreshold) {
        this.terrainWidth = width;
        this.terrainDepth = depth;
        this.maxHeight = maxHeight;

        // Load heightmap image
        BufferedImage img;
        try {
            img = ImageIO.read(new File(heightmapPath));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load heightmap: " + heightmapPath, e);
        }

        int imgW = img.getWidth();
        int imgH = img.getHeight();
        System.out.println("Heightmap loaded: " + imgW + "x" + imgH);

        // Store full-res height data for collision detection
        gridW = imgW;
        gridH = imgH;
        heightData = new float[imgW][imgH];

        for (int x = 0; x < imgW; x++) {
            for (int z = 0; z < imgH; z++) {
                int rgb = img.getRGB(x, z);
                int gray = (rgb >> 16) & 0xFF; // red channel (grayscale = all same)

                if (gray <= seaThreshold) {
                    heightData[x][z] = -30f; // slightly below sea level
                } else {
                    // Map seaThreshold..255 to 0..maxHeight
                    float normalized = (float)(gray - seaThreshold) / (255f - seaThreshold);
                    heightData[x][z] = normalized * maxHeight;
                }
            }
        }

        // Generate mesh at lower resolution
        int meshW = MESH_RES_X;
        int meshH = MESH_RES_Z;
        int vertexCount = meshW * meshH;

        float[] positions = new float[vertexCount * 3];
        float[] normals   = new float[vertexCount * 3];
        float[] uvs       = new float[vertexCount * 2];
        int[]   indices   = new int[6 * (meshW - 1) * (meshH - 1)];

        int vp = 0;
        for (int gz = 0; gz < meshH; gz++) {
            for (int gx = 0; gx < meshW; gx++) {
                float u = (float) gx / (meshW - 1);
                float v = (float) gz / (meshH - 1);

                float worldX = u * width - (width / 2f);
                float worldZ = v * depth - (depth / 2f);
                float worldY = sampleHeight(u, v);

                positions[vp * 3]     = worldX;
                positions[vp * 3 + 1] = worldY;
                positions[vp * 3 + 2] = worldZ;

                // Normal from finite differences
                float hL = sampleHeight(u - 1f / meshW, v);
                float hR = sampleHeight(u + 1f / meshW, v);
                float hD = sampleHeight(u, v - 1f / meshH);
                float hU = sampleHeight(u, v + 1f / meshH);
                float stepX = width / meshW;
                float stepZ = depth / meshH;
                Vector3f normal = new Vector3f(hL - hR, 2f * stepX, hD - hU).normalize();
                normals[vp * 3]     = normal.x;
                normals[vp * 3 + 1] = normal.y;
                normals[vp * 3 + 2] = normal.z;

                uvs[vp * 2]     = u;
                uvs[vp * 2 + 1] = v;

                vp++;
            }
        }

        // Indices
        int ip = 0;
        for (int gz = 0; gz < meshH - 1; gz++) {
            for (int gx = 0; gx < meshW - 1; gx++) {
                int tl = gz * meshW + gx;
                int tr = tl + 1;
                int bl = (gz + 1) * meshW + gx;
                int br = bl + 1;
                indices[ip++] = tl;
                indices[ip++] = bl;
                indices[ip++] = tr;
                indices[ip++] = tr;
                indices[ip++] = bl;
                indices[ip++] = br;
            }
        }

        this.indexCount = indices.length;

        // Upload to GPU
        vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        createVBO(0, 3, positions);
        createVBO(1, 3, normals);
        createVBO(2, 2, uvs);

        IntBuffer indicesBuffer = MemoryUtil.memAllocInt(indices.length);
        indicesBuffer.put(indices).flip();
        int ebo = glGenBuffers();
        vboIds.add(ebo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);
        MemoryUtil.memFree(indicesBuffer);

        glBindVertexArray(0);

        System.out.println("HeightmapTerrain created: " + meshW + "x" + meshH +
                " vertices, " + (indexCount / 3) + " triangles");
    }

    /**
     * Sample height at UV coordinates (0-1 range)
     */
    private float sampleHeight(float u, float v) {
        u = Math.max(0, Math.min(1, u));
        v = Math.max(0, Math.min(1, v));

        float fx = u * (gridW - 1);
        float fz = v * (gridH - 1);

        int x0 = (int) fx;
        int z0 = (int) fz;
        int x1 = Math.min(x0 + 1, gridW - 1);
        int z1 = Math.min(z0 + 1, gridH - 1);

        float fracX = fx - x0;
        float fracZ = fz - z0;

        // Bilinear interpolation
        float h00 = heightData[x0][z0];
        float h10 = heightData[x1][z0];
        float h01 = heightData[x0][z1];
        float h11 = heightData[x1][z1];

        float h0 = h00 + (h10 - h00) * fracX;
        float h1 = h01 + (h11 - h01) * fracX;
        return h0 + (h1 - h0) * fracZ;
    }

    /**
     * Get height at world coordinates (for collision detection)
     */
    public float getHeightAt(float worldX, float worldZ) {
        float u = (worldX + terrainWidth / 2f) / terrainWidth;
        float v = (worldZ + terrainDepth / 2f) / terrainDepth;

        if (u < 0 || u > 1 || v < 0 || v > 1) return -30f; // off-map = sea

        return sampleHeight(u, v);
    }

    private void createVBO(int index, int size, float[] data) {
        FloatBuffer buffer = MemoryUtil.memAllocFloat(data.length);
        buffer.put(data).flip();
        int vbo = glGenBuffers();
        vboIds.add(vbo);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
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
        for (int vbo : vboIds) glDeleteBuffers(vbo);
        glBindVertexArray(0);
        glDeleteVertexArrays(vaoId);
    }
}