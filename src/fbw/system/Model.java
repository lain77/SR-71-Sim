package fbw.system;

import org.lwjgl.assimp.*;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class Model {
    private final int vaoId;
    private final List<Integer> vboIds = new ArrayList<>();
    private final int vertexCount;

    private Model(int vaoId, List<Integer> vboIds, int vertexCount) {
        this.vaoId = vaoId;
        this.vboIds.addAll(vboIds);
        this.vertexCount = vertexCount;
    }

    public void render() {
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glBindVertexArray(0);
        for (int vbo : vboIds) glDeleteBuffers(vbo);
        glDeleteVertexArrays(vaoId);
    }

    public static Model loadFromMesh(AIMesh mesh) {
        // collect vertices/normals/indices
        AIVector3D.Buffer verts = mesh.mVertices();
        AIVector3D.Buffer normals = mesh.mNormals();
        int numVertices = verts != null ? verts.remaining() : 0;

        FloatBuffer verticesBuffer = MemoryUtil.memAllocFloat(numVertices * 3);
        FloatBuffer normalsBuffer = MemoryUtil.memAllocFloat(numVertices * 3);

        for (int i = 0; i < numVertices; i++) {
            AIVector3D v = verts.get(i);
            verticesBuffer.put(v.x()).put(v.y()).put(v.z());
            if (normals != null) {
                AIVector3D n = normals.get(i);
                normalsBuffer.put(n.x()).put(n.y()).put(n.z());
            } else {
                normalsBuffer.put(0f).put(0f).put(1f);
            }
        }
        verticesBuffer.flip();
        normalsBuffer.flip();

        // indices
        AIFace.Buffer faces = mesh.mFaces();
        int faceCount = faces != null ? faces.remaining() : 0;
        int indexCount = faceCount * 3;
        IntBuffer indicesBuffer = MemoryUtil.memAllocInt(indexCount);
        for (int i = 0; i < faceCount; i++) {
            AIFace face = faces.get(i);
            IntBuffer idx = face.mIndices();
            for (int j = 0; j < idx.remaining(); j++) indicesBuffer.put(idx.get(j));
        }
        indicesBuffer.flip();

        // create VAO/VBOs
        int vaoId = glGenVertexArrays();
        glBindVertexArray(vaoId);

        List<Integer> vboList = new ArrayList<>();

        // vertices VBO (location 0)
        int vboPos = glGenBuffers();
        vboList.add(vboPos);
        glBindBuffer(GL_ARRAY_BUFFER, vboPos);
        glBufferData(GL_ARRAY_BUFFER, verticesBuffer, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0);

        // normals VBO (location 1)
        int vboNorm = glGenBuffers();
        vboList.add(vboNorm);
        glBindBuffer(GL_ARRAY_BUFFER, vboNorm);
        glBufferData(GL_ARRAY_BUFFER, normalsBuffer, GL_STATIC_DRAW);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 0, 0);

        // indices
        int idxVbo = glGenBuffers();
        vboList.add(idxVbo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, idxVbo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indicesBuffer, GL_STATIC_DRAW);

        // unbind
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        // free temp buffers
        MemoryUtil.memFree(verticesBuffer);
        MemoryUtil.memFree(normalsBuffer);
        MemoryUtil.memFree(indicesBuffer);

        return new Model(vaoId, vboList, indexCount);
    }
    
    public static List<Model> loadAllFromScene(AIScene scene) {
        List<Model> models = new ArrayList<>();

        processNode(scene.mRootNode(), scene, models);

        return models;
    }

    private static void processNode(AINode node, AIScene scene, List<Model> models) {
        if (node.mMeshes() != null) {
            for (int i = 0; i < node.mNumMeshes(); i++) {
                int meshIndex = node.mMeshes().get(i);
                AIMesh mesh = AIMesh.create(scene.mMeshes().get(meshIndex));
                models.add(Model.loadFromMesh(mesh));
            }
        }

        if (node.mChildren() != null) {
            for (int i = 0; i < node.mNumChildren(); i++) {
                AINode child = AINode.create(node.mChildren().get(i));
                processNode(child, scene, models);
            }
        }
    }
}
