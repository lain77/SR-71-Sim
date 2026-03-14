package tests;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Testr {

    private long window;
    private int width = 1280;
    private int height = 720;

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        // Inicializa GLFW
        if (!glfwInit()) throw new IllegalStateException("Não foi possível inicializar GLFW");

        // Configura GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        // Cria janela
        window = glfwCreateWindow(width, height, "Mundo 3D do Avião", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Falha ao criar a janela");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1); // VSync
        glfwShowWindow(window);

        // Cria capacidades OpenGL
        GL.createCapabilities();

        // Configura viewport e profundidade
        glViewport(0, 0, width, height);
        glEnable(GL_DEPTH_TEST);

        // Cor do céu
        glClearColor(0.5f, 0.7f, 1f, 1f);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // Configura perspectiva 3D
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            float aspect = (float) width / height;
            float fov = 70f;
            float near = 0.1f;
            float far = 1000f;
            float yScale = (float)(1f / Math.tan(Math.toRadians(fov / 2f)));
            float xScale = yScale / aspect;

            FloatBuffer perspective = BufferUtils.createFloatBuffer(16);
            perspective.put(new float[]{
                    xScale, 0, 0, 0,
                    0, yScale, 0, 0,
                    0, 0, -(far + near)/(far - near), -1,
                    0, 0, -(2*far*near)/(far - near), 0
            }).flip();
            glMultMatrixf(perspective);

            // Modelo/câmera
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();

            // posição da câmera
            glTranslatef(0f, -5f, -20f);
            glRotatef(20f, 1f, 0f, 0f);

            renderGround();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void renderGround() {
        glPushMatrix();
        glColor3f(0.2f, 0.6f, 0.2f); // Cor das linhas do chão

        float size = 100f;    // Meio tamanho do grid
        float step = 5f;      // Espaçamento entre as linhas

        glBegin(GL_LINES);
        // Linhas paralelas ao eixo X
        for (float z = -size; z <= size; z += step) {
            glVertex3f(-size, 0f, z);
            glVertex3f(size, 0f, z);
        }
        // Linhas paralelas ao eixo Z
        for (float x = -size; x <= size; x += step) {
            glVertex3f(x, 0f, -size);
            glVertex3f(x, 0f, size);
        }
        glEnd();

        glPopMatrix();
    }


    private void cleanup() {
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    public static void main(String[] args) {
        new Testr().run();
    }
}
