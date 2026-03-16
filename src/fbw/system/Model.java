package fbw.system;

import org.lwjgl.assimp.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBImage.*;

public class Model {
    private final int vaoId;
    private final List<Integer> vboIds = new ArrayList<>();
    private final int vertexCount;
    private int textureId = -1;

    private Model(int vaoId, List<Integer> vboIds, int vertexCount) {
        this.vaoId       = vaoId;
        this.vboIds.addAll(vboIds);
        this.vertexCount = vertexCount;
    }

    public int getTextureId() { return textureId; }

    public void render() {
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glBindVertexArray(0);
        for (int vbo : vboIds) glDeleteBuffers(vbo);
        glDeleteVertexArrays(vaoId);
        if (textureId > 0) glDeleteTextures(textureId);
    }

    public static Model loadFromMesh(AIMesh mesh) {
        AIVector3D.Buffer verts      = mesh.mVertices();
        AIVector3D.Buffer normals    = mesh.mNormals();
        AIVector3D.Buffer textCoords = mesh.mTextureCoords(0);

        int numVertices = verts != null ? verts.remaining() : 0;

        FloatBuffer verticesBuffer   = MemoryUtil.memAllocFloat(numVertices * 3);
        FloatBuffer normalsBuffer    = MemoryUtil.memAllocFloat(numVertices * 3);
        FloatBuffer textCoordsBuffer = MemoryUtil.memAllocFloat(numVertices * 2);

        for (int i = 0; i < numVertices; i++) {
            AIVector3D v = verts.get(i);
            verticesBuffer.put(v.x()).put(v.y()).put(v.z());

            if (normals != null) {
                AIVector3D n = normals.get(i);
                normalsBuffer.put(n.x()).put(n.y()).put(n.z());
            } else {
                normalsBuffer.put(0f).put(0f).put(1f);
            }

            if (textCoords != null) {
                AIVector3D tc = textCoords.get(i);
                textCoordsBuffer.put(tc.x()).put(tc.y());
            } else {
                textCoordsBuffer.put(0f).put(0f);
            }
        }
        verticesBuffer.flip();
        normalsBuffer.flip();
        textCoordsBuffer.flip();

        AIFace.Buffer faces     = mesh.mFaces();
        int           faceCount = faces != null ? faces.remaining() : 0;
        IntBuffer indicesBuffer = MemoryUtil.memAllocInt(faceCount * 3);
        for (int i = 0; i < faceCount; i++) {
            AIFace  face = faces.get(i);
            IntBuffer idx = face.mIndices();
            for (int j = 0; j < idx.remaining(); j++) indicesBuffer.put(idx.get(j));
        }
        indicesBuffer.flip();

        int vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);
        List<Integer> vboList = new ArrayList<>();

        int vboPos = glGenBuffers(); vboList.add(vboPos);
        glBindBuffer(GL_ARRAY_BUFFER, vboPos);
        glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

        int vboNorm = glGenBuffers(); vboList.add(vboNorm);
        glBindBuffer(GL_ARRAY_BUFFER, vboNorm);
        glBufferData(GL_ARRAY_BUFFER, normalsBuffer, GL_STATIC_DRAW);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0);

        int vboTex = glGenBuffers(); vboList.add(vboTex);
        glBindBuffer(GL_ARRAY_BUFFER, vboTex);
        glBufferData(GL_ARRAY_BUFFER, textCoordsBuffer, GL_STATIC_DRAW);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, 0, 0);

        int idxVbo = glGenBuffers(); vboList.add(idxVbo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        MemoryUtil.memFree(verticesBuffer);
        MemoryUtil.memFree(normalsBuffer);
        MemoryUtil.memFree(textCoordsBuffer);
        MemoryUtil.memFree(indicesBuffer);

        return new Model(vaoId, vboList, faceCount * 3);
    }

    public static List<Model> loadAllFromScene(AIScene scene) {
        List<Model> models = new ArrayList<>();
        processNode(scene.mRootNode(), scene, models);
        return models;
    }

    private static void processNode(AINode node, AIScene scene, List<Model> models) {
        if (node.mMeshes() != null) {
            for (int i = 0; i < node.mNumMeshes(); i++) {
                int    meshIndex = node.mMeshes().get(i);
                AIMesh mesh      = AIMesh.create(scene.mMeshes().get(meshIndex));
                Model  model     = Model.loadFromMesh(mesh);

                // Extrai textura do material associado à mesh
                int matIndex = mesh.mMaterialIndex();
                if (scene.mMaterials() != null && matIndex >= 0
                        && matIndex < scene.mNumMaterials()) {
                    AIMaterial mat = AIMaterial.create(scene.mMaterials().get(matIndex));
                    model.textureId = loadTextureFromMaterial(mat, scene);
                }

                models.add(model);
            }
        }

        if (node.mChildren() != null) {
            for (int i = 0; i < node.mNumChildren(); i++) {
                processNode(AINode.create(node.mChildren().get(i)), scene, models);
            }
        }
    }

    // Extrai textura embutida no GLB (textura em memória, não em arquivo externo)
    private static int loadTextureFromMaterial(AIMaterial mat, AIScene scene) {
        AIString path = AIString.calloc();
        int result = Assimp.aiGetMaterialTexture(mat,
            Assimp.aiTextureType_DIFFUSE, 0, path,
            (IntBuffer) null, null, null, null, null, null);

        if (result != Assimp.aiReturn_SUCCESS) { path.free(); return -1; }

        String texPath = path.dataString();
        path.free();

        // Textura embutida no GLB — caminho começa com '*'
        if (texPath.startsWith("*")) {
            int texIndex = Integer.parseInt(texPath.substring(1));
            if (scene.mTextures() == null || texIndex >= scene.mNumTextures()) return -1;

            AITexture aiTex = AITexture.create(scene.mTextures().get(texIndex));

            // Textura comprimida (PNG/JPG embutido) — decodifica com STB
            if (aiTex.mHeight() == 0) {
                ByteBuffer compressedData = aiTex.pcDataCompressed();
                IntBuffer  w = MemoryUtil.memAllocInt(1);
                IntBuffer  h = MemoryUtil.memAllocInt(1);
                IntBuffer  c = MemoryUtil.memAllocInt(1);

                stbi_set_flip_vertically_on_load(false);
                ByteBuffer pixels = stbi_load_from_memory(compressedData, w, h, c, 4);
                if (pixels == null) {
                    MemoryUtil.memFree(w); MemoryUtil.memFree(h); MemoryUtil.memFree(c);
                    return -1;
                }

                int texId = uploadTexture(pixels, w.get(0), h.get(0));
                stbi_image_free(pixels);
                MemoryUtil.memFree(w); MemoryUtil.memFree(h); MemoryUtil.memFree(c);
                return texId;
            }
        }
        return -1;
    }

    private static int uploadTexture(ByteBuffer pixels, int w, int h) {
        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glGenerateMipmap(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);
        return texId;
    }
}