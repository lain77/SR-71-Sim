package fbw.system;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private final Vector3f position = new Vector3f();
    private final Vector3f target = new Vector3f();
    private final Vector3f up = new Vector3f(0, 1, 0);

    private float near = 1f;
    private float far = 2000f; 
    private float fov = 70f;
    private float aspect = 1280f/720f; //atualizar windowWidth/windowHeight


    public Camera() {}

    public void setPosition(float x, float y, float z) {
        position.set(x,y,z);
    }

    public void lookAt(float tx, float ty, float tz) {
        target.set(tx,ty,tz);
    }

    public void setAspect(float aspect) { this.aspect = aspect; }

    public Matrix4f getViewMatrix() {
        return new Matrix4f().lookAt(position, target, up);
    }

    public Matrix4f getProjectionMatrix() {
        return new Matrix4f().perspective((float)Math.toRadians(fov), aspect, near, far);
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
            case 2: // Visão externa atrás
                position.set(airplanePos.x - 10f, airplanePos.y + 5f, airplanePos.z + 15f);
                target.set(airplanePos.x, airplanePos.y, airplanePos.z);
                break;
        }
    }

    public void updateAspect(int width, int height) {
        this.aspect = (float) width / height;
    }
    
    public Vector3f getPosition() {
		return position;
	}
    
	public float getNear() {
		return near;
	}

	public void setNear(float near) {
		this.near = near;
	}

	public float getFar() {
		return far;
	}

	public void setFar(float far) {
		this.far = far;
	}

	public Vector3f getTarget() {
		return target;
	}

	public Vector3f getUp() {
		return up;
	}

	public float getFov() {
		return fov;
	}

	public float getAspect() {
		return aspect;
	}

	// convenience
    public void setFov(float fov) { this.fov = fov; }
    public void setNearFar(float near, float far) { this.near = near; this.far = far; }
}
