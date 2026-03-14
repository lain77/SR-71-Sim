package fbw.system;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private final Vector3f position = new Vector3f();
    private final Vector3f target = new Vector3f();
    private final Vector3f up = new Vector3f(0, 1, 0);

    private float near = 1f;
    private float far = 150000f; 
    private float fov = 70f;
    private float aspect = 1280f/720f;

    // --- VARIÁVEIS PARA A CÂMERA ORBITAL DO MOUSE ---
    private float yaw = 0f;
    private float pitch = 15f; // Começa olhando um pouquinho de cima
    private float distance = 15f; // Quão longe a câmera fica do avião

    public Camera() {}

    public void setPosition(float x, float y, float z) { position.set(x,y,z); }
    public void lookAt(float tx, float ty, float tz) { target.set(tx,ty,tz); }
    public void setAspect(float aspect) { this.aspect = aspect; }

    public Matrix4f getViewMatrix() { return new Matrix4f().lookAt(position, target, up); }
    public Matrix4f getProjectionMatrix() { return new Matrix4f().perspective((float)Math.toRadians(fov), aspect, near, far); }

    // --- CONTROLE DO MOUSE ---
    public void addRotation(float dYaw, float dPitch) {
        this.yaw += dYaw;
        this.pitch += dPitch;
        // Limita o pitch para a câmera não dar uma cambalhota passando por baixo do chão
        if (this.pitch > 89.0f) this.pitch = 89.0f;
        if (this.pitch < -10.0f) this.pitch = -10.0f; 
    }

    public void addDistance(float dDist) {
        this.distance += dDist;
        if (this.distance < 5f) this.distance = 5f; // Zoom máximo
    }

    public void setMode(int mode, Vector3f airplanePos) {
        switch(mode) {
            case 0: // Cockpit
                position.set(airplanePos.x, airplanePos.y + 1f, airplanePos.z + 2f);
                target.set(airplanePos.x, airplanePos.y, airplanePos.z + 10f);
                break;
            case 1: // Mapa (top-down)
                position.set(airplanePos.x, airplanePos.y + 50f, airplanePos.z);
                target.set(airplanePos.x, airplanePos.y, airplanePos.z);
                break;
            case 2: // Visão Externa (AGORA COM ORBITA)
                // Matemática para fazer a câmera orbitar em volta do avião
                float horizontalDistance = distance * (float) Math.cos(Math.toRadians(pitch));
                float verticalDistance = distance * (float) Math.sin(Math.toRadians(pitch));

                float offsetX = horizontalDistance * (float) Math.sin(Math.toRadians(yaw));
                float offsetZ = horizontalDistance * (float) Math.cos(Math.toRadians(yaw));

                position.set(airplanePos.x - offsetX, airplanePos.y + verticalDistance, airplanePos.z - offsetZ);
                target.set(airplanePos.x, airplanePos.y, airplanePos.z);
                break;
        }
    }

    public void updateAspect(int width, int height) { this.aspect = (float) width / height; }
    
    // Getters e Setters padrão
    public Vector3f getPosition() { return position; }
    public float getNear() { return near; }
    public void setNear(float near) { this.near = near; }
    public float getFar() { return far; }
    public void setFar(float far) { this.far = far; }
    public Vector3f getTarget() { return target; }
    public Vector3f getUp() { return up; }
    public float getFov() { return fov; }
    public float getAspect() { return aspect; }
    public void setFov(float fov) { this.fov = fov; }
    public void setNearFar(float near, float far) { this.near = near; this.far = far; }
}