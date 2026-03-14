package fbw.system;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.assimp.AIMesh;

public class Scene3D {

    private Camera camera;
    private Model airplane;
    private ShaderProgram shader;

    public void init(FlyByWire fbw) throws Exception {
        camera = new Camera();
        shader = new ShaderProgram("vertex.glsl", "fragment.glsl");
    }

    /**
     * Renderiza a cena 3D com base na câmera escolhida
     * @param cameraMode 0=cockpit, 1=mapa, 2=externa
     * @param airplanePos posição atual do avião
     */
    public void render(int cameraMode, Vector3f airplanePos) {
        shader.bind();

        // Atualiza posição da câmera de acordo com o modo
        camera.setMode(cameraMode, airplanePos);

        // Envia matrizes para o shader
        shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
        shader.setUniformMatrix4f("view", camera.getViewMatrix());

        // Modelo traduzido para a posição do avião
        Matrix4f modelMat = new Matrix4f().translation(airplanePos);
        shader.setUniformMatrix4f("model", modelMat);

        // Renderiza o avião
        airplane.render();

        shader.unbind();
    }

    public void cleanup() {
        airplane.cleanup();
        shader.cleanup();
    }
}
