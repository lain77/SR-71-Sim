package fbw.system;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.assimp.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.*;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

import javax.imageio.ImageIO;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBImage.*;
import static org.lwjgl.system.MemoryUtil.*;

import fbw.assets.Audio;
import fbw.assets.FileUtils;
import fbw.assets.Text;

public class FlyData {

    Audio audio = new Audio();
    private ShaderProgram shader;
    private Camera camera;
    private List<Model> models = new ArrayList<>(); 

    private long window;
    private int mapTextureId;
    private int backgroundTextureId;

    private boolean escStartPressed = false;
    private boolean upStartPressed = false;
    private boolean downStartPressed = false;
    private boolean aStartPressed = false;
    private boolean dStartPressed = false;
    private boolean rightMouseDown = false;
    private double lastMouseX = -1;
    private double lastMouseY = -1;
    private boolean countermeasureActive = false;
    
    private boolean showModel = true;
    private float countermeasureX = 0;
    private float countermeasureY = 0;

    private Text textRenderer;
    private Enemy enemy;
    private int cameraMode = 0;
    private FlyByWire fbw;
    private AIScene scene;

    private int sEsquerdaX = 50, sEsquerdaY = 50;
    private int sDireitaX = 1000, sDireitaY = 50;
    private int sCentroX = 680, sCentroY = 50;

    private enum Screen { START, DATA, MAP, EXTERNAL }
    private Screen currentScreen = Screen.START;
    private Screen previousScreen = Screen.START;
    
    private GroundModel ground;
    private int groundTextureId;

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        if (!glfwInit()) throw new IllegalStateException("GLFW init falhou");

        window = glfwCreateWindow(1280, 720, "Simulador Fly-By-Wire", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Falha ao criar janela");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        setupInput();

        glEnable(GL_TEXTURE_2D);

        textRenderer = new Text();
        try { textRenderer.init(); }
        catch (IOException e) { throw new RuntimeException("Erro ao carregar fonte", e); }

        fbw = new FlyByWire();
        fbw.start();

        enemy = new Enemy(this, fbw);
        enemy.start();

        Mundo mundo = new Mundo(10000f, 5000f, 10000f, fbw);
        mundo.start();
        Vector3f centro = new Vector3f(mundo.getTamanho()).mul(0.5f);
        mundo.getAviao().setPosicao(centro);

        mapTextureId = loadTexture("src/img/EuropeMap.PNG");
        backgroundTextureId = loadBackgroundImage();

        groundTextureId = loadTexture("src/img/ground.jpg");
        
        ground = new GroundModel(400000f, 2000f);
        
        init3DSystem();
    }

    private void init3DSystem() {
        try {
            System.out.println("initializing 3D engine");
            String vertexSrc = FileUtils.loadFileAsString("src/res/shaders/vertex.glsl");
            String fragmentSrc = FileUtils.loadFileAsString("src/res/shaders/fragment.glsl");
            shader = new ShaderProgram(vertexSrc, fragmentSrc);

            camera = new Camera();
            camera.updateAspect(1280, 720);

            scene = Assimp.aiImportFile("src/models/sr71/sr71nochute.glb",
                    Assimp.aiProcess_Triangulate | Assimp.aiProcess_FlipUVs | Assimp.aiProcess_GenNormals);

            if (scene == null || scene.mRootNode() == null) {
                throw new RuntimeException("Erro ao carregar modelo: " + Assimp.aiGetErrorString());
            }

            models.addAll(Model.loadAllFromScene(scene)); 
            System.out.println("✅ Modelos carregados: " + models.size());

        } catch (Exception e) {
            throw new RuntimeException("Erro na inicialização do 3D", e);
        }
    }

    private void setupInput() {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            boolean pressed = action == GLFW_PRESS;
            switch (key) {
                case GLFW_KEY_ENTER -> { if (pressed && currentScreen == Screen.START) currentScreen = Screen.DATA; }
                case GLFW_KEY_ESCAPE -> escStartPressed = pressed;
                case GLFW_KEY_UP -> upStartPressed = pressed;
                case GLFW_KEY_DOWN -> downStartPressed = pressed;
                case GLFW_KEY_A -> aStartPressed = pressed;
                case GLFW_KEY_D -> dStartPressed = pressed;
                case GLFW_KEY_C -> { if (pressed) cameraMode = (cameraMode + 1) % 3; }
                case GLFW_KEY_E -> {
                    if (pressed) {
                        if (currentScreen == Screen.DATA) currentScreen = Screen.EXTERNAL;
                        else if (currentScreen == Screen.EXTERNAL) currentScreen = Screen.DATA;
                    }
                }
                case GLFW_KEY_M -> {
                    if (pressed) {
                        if (currentScreen != Screen.MAP) {
                            previousScreen = currentScreen;
                            currentScreen = Screen.MAP;
                        } else {
                            currentScreen = previousScreen;
                        }
                    }
                }
                case GLFW_KEY_S -> {
                    if (pressed && enemy.isMissileWarning()) {
                        fbw.activateCountermeasure();
                    }
                }
                case GLFW_KEY_K -> {
                    if (pressed) showModel = !showModel;
                }
            }
        });
     // Escuta os cliques do mouse
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_RIGHT) { // Segura botão direito para girar
                rightMouseDown = (action == GLFW_PRESS);
                if (!rightMouseDown) {
                    // Reseta quando solta o botão para não dar pulos bruscos
                    lastMouseX = -1;
                    lastMouseY = -1;
                }
            }
        });

        // Escuta o movimento do cursor
        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            // Só move a câmera se estiver segurando o botão direito E na visão externa
            if (rightMouseDown && currentScreen == Screen.EXTERNAL) {
                if (lastMouseX != -1 && lastMouseY != -1) {
                    float dx = (float) (xpos - lastMouseX);
                    float dy = (float) (ypos - lastMouseY);
                    
                    float sensitivity = 0.3f; // Ajuste se achar muito rápido/lento
                    camera.addRotation(dx * sensitivity, -dy * sensitivity); 
                }
                lastMouseX = xpos;
                lastMouseY = ypos;
            }
        });
        
     // Escuta a rodinha do mouse (Scroll) para o Zoom
        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (currentScreen == Screen.EXTERNAL) {
                float zoomSensitivity = 2.0f; // Ajuste a velocidade do zoom aqui
                // yoffset é positivo quando roda para frente, negativo para trás.
                // Invertemos o sinal para que rolar para frente diminua a distância (aproxime)
                camera.addDistance((float) -yoffset * zoomSensitivity);
            }
        });
    }

    private void setup2DLegado() {
        // ESSENCIAL: Garantir que nenhum shader está rodando antes de fazer coisas legadas
        glUseProgram(0); 
        glBindVertexArray(0);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, 1280, 720, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            if (escStartPressed) break;

            // Lógica de Voo
            if (aStartPressed) fbw.climbAltitude(0.01);
            if (dStartPressed) fbw.decreaseAltitude(0.01);
            if (upStartPressed) fbw.throttleUp(0.1);
            if (downStartPressed) fbw.throttleDown(0.1);

            switch (currentScreen) {
                case START -> renderStartScreen();
                case DATA -> renderDataScreen();
                case MAP -> renderMapScreen();
                case EXTERNAL -> renderExternal();
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void renderStartScreen() {
        setup2DLegado();
        glClearColor(0f, 0f, 0f, 1f);

        glColor3f(1f, 1f, 1f);
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, backgroundTextureId);
        glBegin(GL_QUADS);
        glTexCoord2f(0,1); glVertex2f(0,0);
        glTexCoord2f(1,1); glVertex2f(1280,0);
        glTexCoord2f(1,0); glVertex2f(1280,720);
        glTexCoord2f(0,0); glVertex2f(0,720);
        glEnd();
        glBindTexture(GL_TEXTURE_2D, 0);

        glColor3f(0f,0f,0f);
        textRenderer.renderText("Pressione ENTER", 590, 360);
    }

    private void renderDataScreen() {
        glClearColor(0.1f, 0.1f, 0.1f, 1f);
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        // 1. RENDERIZAÇÃO 3D (MODERNA)
        if (showModel) {
            glEnable(GL_DEPTH_TEST);
            
            // Usamos a câmera JOML para definir a posição
            Vector3f origin = new Vector3f(0,0,0);
            camera.setMode(cameraMode, origin); // Usa a lógica da sua classe Camera

            Matrix4f modelMatrix = new Matrix4f()
                .translate(0f, -2f, -10f) // Posiciona o modelo na frente da câmera
                .rotateX((float)Math.toRadians(-90))
                .rotateZ((float)Math.toRadians(180))
                .rotateY((float)Math.toRadians(90))
                .rotateX((float)Math.toRadians(data.pitch))
                .rotateZ((float)Math.toRadians(data.roll))
                .scale(0.3f);

            shader.bind();
            shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
            shader.setUniformMatrix4f("view", camera.getViewMatrix());
            shader.setUniformMatrix4f("model", modelMatrix);
            
            shader.setUniform3f("objectColor", 0.4f, 0.4f, 0.4f);
            shader.setUniform3f("lightPos", 0f, 100f, 0f);
            shader.setUniform3f("lightColor", 1.0f, 1.0f, 1.0f);
            shader.setUniform3f("viewPos", camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);

            for (Model m : models) m.render();

            shader.unbind(); // Desliga o shader!
        }

        // 2. RENDERIZAÇÃO 2D (HUD LEGADO)
        renderHUD();
        drawPlayer();
        renderCountermeasureHUD();
    }

    private void renderExternal() {
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        glClearColor(0.53f, 0.81f, 0.92f, 1f);
        glEnable(GL_DEPTH_TEST);
        
        Vector3f posReal = fbw.getPosicao();

        Vector3f planePos = new Vector3f(posReal.x, (float)data.getAltitude() / 10f, posReal.z);
        
        camera.setMode(2, planePos); // Modo externa

        shader.bind();
        shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
        shader.setUniformMatrix4f("view", camera.getViewMatrix());
        
        // --- 1. DESENHAR O CHÃO (MODERNO) ---
        shader.setUniform1i("useTexture", 1); // Avisa o shader: "Use textura!"
        shader.setUniform1i("texture1", 0);   // Textura no slot 0
        
        shader.setUniform3f("lightDir", -0.5f, -1.0f, -0.3f); 
        shader.setUniform3f("lightColor", 1.0f, 0.95f, 0.8f); // Luz levemente amarelada
        shader.setUniform3f("skyColor", 0.53f, 0.81f, 0.92f); // Azul do céu
        
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, groundTextureId);
        
        // O chão fica cravado na posição 0,0,0 do mundo
        Matrix4f groundMatrix = new Matrix4f().translate(0, 0, 0); 
        shader.setUniformMatrix4f("model", groundMatrix);
        
        ground.render(); // Desenha o super modelo do chão
        glBindTexture(GL_TEXTURE_2D, 0);

        // --- 2. DESENHAR O AVIÃO (SR-71) ---
        shader.setUniform1i("useTexture", 0); // Avisa o shader: "Use a cor cinza sólida!"
        shader.setUniform3f("objectColor", 0.15f, 0.15f, 0.15f);
        shader.setUniform3f("lightPos", planePos.x, planePos.y + 500f, planePos.z); 
        shader.setUniform3f("lightColor", 1.0f, 1.0f, 1.0f);
        shader.setUniform3f("viewPos", camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);

        Matrix4f modelMatrix = new Matrix4f()
                .translate(planePos)
                
                .rotateY((float)Math.toRadians(data.yaw))   
                .rotateX((float)Math.toRadians(data.pitch))
                .rotateZ((float)Math.toRadians(data.roll))  
                
                .rotateX((float)Math.toRadians(-90)) 
                .rotateZ((float)Math.toRadians(270)) 
                
                .scale(0.8f);
        
        shader.setUniformMatrix4f("model", modelMatrix);

        for (Model m : models) m.render();

        shader.unbind();
        
        // Desenha avisos críticos por cima do 3D
        renderHUD(); 
    }
    private void renderMapScreen() {
        setup2DLegado();
        glClearColor(0f, 0f, 0f, 1f);
        
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, mapTextureId);
        glColor3f(1f, 1f, 1f);

        glBegin(GL_QUADS);
            glTexCoord2f(0f, 0f); glVertex2f(100f, 100f);
            glTexCoord2f(1f, 0f); glVertex2f(1180f, 100f);
            glTexCoord2f(1f, 1f); glVertex2f(1180f, 620f);
            glTexCoord2f(0f, 1f); glVertex2f(100f, 620f);
        glEnd();

        glBindTexture(GL_TEXTURE_2D, 0);

        glColor3f(1f, 1f, 1f);
        textRenderer.renderText("Mapa ativo - pressione M para voltar", 480, 60);
    }

    private void renderHUD() {
        setup2DLegado(); // Prepara o ambiente para textos e quadrados

        glEnable(GL_TEXTURE_2D);
        
        countermeasureActive = enemy.isMissileWarning();
        FlyByWire.FlightData data = fbw.getLastData();
        
        if (data != null) {
            textRenderer.renderText("Alt: " + data.getAltitude(), sEsquerdaX, sEsquerdaY);
            textRenderer.renderText("Pitch: " + data.getPitch(), sDireitaX + 20, sDireitaY);
            textRenderer.renderText("Roll: " + data.getRoll(), sCentroX, sCentroY);
            textRenderer.renderText("Speed: " + String.format("%.1f km/h", data.getSpeed()*3.6), sEsquerdaX, sEsquerdaY+30);
            textRenderer.renderText("MACH: " + data.getMach(), sEsquerdaX, sEsquerdaY+60);
            textRenderer.renderText("X: " + fbw.getPosicao().x, sDireitaX, sDireitaY + 40);
            textRenderer.renderText("Face: " + fbw.getDirecao().x, sDireitaX - 300, sDireitaY + 40);
        }

        if (enemy.isMissileWarning()) { 
            glColor3f(1f, 0f, 0f);
            textRenderer.renderText("INCOMING MISSILE!", 500, 200);
            glColor3f(1f, 1f, 1f);
        }
    }

    private void drawPlayer() {
        setup2DLegado();
        
        // Garante que nenhuma textura de texto interfira nas linhas
        glDisable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);
        
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        double altMin = 0, altMax = 85000;
        double normalized = Math.max(0, Math.min(1, (data.getAltitude() - altMin) / (altMax - altMin)));
        float y = (float)(600f - normalized * (600f - 400f));

        glLineWidth(2f);
        glColor3f(1f, 0f, 0f);

        glBegin(GL_LINES);
        glVertex2f(80f, y);
        glVertex2f(100f, y);

        glColor3f(1f, 1f, 1f);
        // BARRINHA DE CIMA E LADOS
        glVertex2f(60f, 375); glVertex2f(120f, 375);
        glVertex2f(60f, 675); glVertex2f(120f, 675);
        glVertex2f(60f, 375); glVertex2f(60f, 675);
        glVertex2f(120f, 375); glVertex2f(120f, 675);
        glEnd();

        glLineWidth(1f);
    }

    private void renderCountermeasureHUD() {
        if (!countermeasureActive) return;
        setup2DLegado();
        
        // Desativa texturas para garantir um quadrado verde sólido
        glDisable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);

        float boxWidth = 120f, boxHeight = 50f;
        float boxX = 1280f - boxWidth - 50f;
        float boxY = 720f - boxHeight - 80f;

        glColor3f(0f, 1f, 0f);
        glBegin(GL_QUADS);
        glVertex2f(boxX, boxY);
        glVertex2f(boxX + boxWidth, boxY);
        glVertex2f(boxX + boxWidth, boxY + boxHeight);
        glVertex2f(boxX, boxY + boxHeight);
        glEnd();

        glColor3f(1f, 1f, 1f);
        textRenderer.renderText("ACTIVATE", boxX + 15f, boxY + 15f);
    }

    // Temporariamente mantido como legado, mas as matrizes agora vêm da câmera JOML
    private void renderGround(Vector3f planePos) {
        glUseProgram(0); // Desliga shader caso estivesse ligado
        glBindVertexArray(0);
        
        // Evita que o chão puxe alguma textura residual
        glDisable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        FloatBuffer fbProj = BufferUtils.createFloatBuffer(16);
        camera.getProjectionMatrix().get(fbProj);
        glLoadMatrixf(fbProj);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        FloatBuffer fbView = BufferUtils.createFloatBuffer(16);
        camera.getViewMatrix().get(fbView);
        glLoadMatrixf(fbView);

        glColor3f(0.2f, 0.6f, 0.2f);
        float size = 500f; 
        float step = 20f;

        glBegin(GL_LINES);
        for (float z = -size; z <= size; z += step) {
            glVertex3f(planePos.x - size, 0f, planePos.z + z);
            glVertex3f(planePos.x + size, 0f, planePos.z + z);
        }
        for (float x = -size; x <= size; x += step) {
            glVertex3f(planePos.x + x, 0f, planePos.z - size);
            glVertex3f(planePos.x + x, 0f, planePos.z + size);
        }
        glEnd();
    }

    private void cleanup() {
        glDeleteTextures(mapTextureId);
        glDeleteTextures(backgroundTextureId);
        if (scene != null) Assimp.aiReleaseImport(scene);
        for (Model m : models) m.cleanup();
        if (shader != null) shader.cleanup();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private int loadTexture(String filePath) {
        BufferedImage image;
        try {
            image = ImageIO.read(new File(filePath));
        } catch (IOException e) {
            throw new RuntimeException("Erro ao carregar textura: " + filePath, e);
        }

        int width = image.getWidth();
        int height = image.getHeight();

        int[] pixels_raw = new int[width * height];
        image.getRGB(0, 0, width, height, pixels_raw, 0, width);

        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels_raw[y * width + x];
                pixels.put((byte) ((pixel >> 16) & 0xFF));
                pixels.put((byte) ((pixel >> 8) & 0xFF));
                pixels.put((byte) (pixel & 0xFF));
                pixels.put((byte) ((pixel >> 24) & 0xFF));
            }
        }
        pixels.flip();

        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);

        return texId;
    }

    private int loadBackgroundImage() {
        ByteBuffer imageBuffer;
        try (InputStream is = getClass().getResourceAsStream("/img/sr7.jpg")) {
            if (is == null) throw new RuntimeException("Imagem não encontrada");
            byte[] imageBytes = is.readAllBytes();
            imageBuffer = BufferUtils.createByteBuffer(imageBytes.length).put(imageBytes);
            imageBuffer.flip();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao carregar imagem", e);
        }

        IntBuffer width = BufferUtils.createIntBuffer(1);
        IntBuffer height = BufferUtils.createIntBuffer(1);
        IntBuffer channels = BufferUtils.createIntBuffer(1);

        stbi_set_flip_vertically_on_load(true);
        ByteBuffer image = stbi_load_from_memory(imageBuffer, width, height, channels, 4);
        if (image == null) throw new RuntimeException("Erro STB: " + stbi_failure_reason());

        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(), height.get(), 0,
                GL_RGBA, GL_UNSIGNED_BYTE, image);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        stbi_image_free(image);

        return texId;
    }

    public static void main(String[] args) { new FlyData().run(); }
}