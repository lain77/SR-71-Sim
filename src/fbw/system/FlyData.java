package fbw.system;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.stb.*;
import org.lwjgl.assimp.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.*;
import java.util.ArrayList;
import java.util.List;

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
    // private Model model; 
    private List<Model> models = new ArrayList<>(); 

    private long window;
    private int mapTextureId;
    private int backgroundTextureId;

    private boolean enterStartPressed = false;
    private boolean escStartPressed = false;
    private boolean upStartPressed = false;
    private boolean downStartPressed = false;
    private boolean aStartPressed = false;
    private boolean dStartPressed = false;
    private boolean countermeasureActive = false;
    private boolean countermeasurePressed = false;
    
    private boolean showModel = true;

    private float countermeasureX = 0;
    private float countermeasureY = 0;

    private Text textRenderer;
    private Enemy enemy;
    private int cameraMode = 0;
    private FlyByWire fbw;

    private AIScene scene;

    private boolean mapShowing = false;

    private int sEsquerdaX = 50;
    private int sEsquerdaY = 50;
    private int sDireitaX = 1000;
    private int sDireitaY = 50;
    private int sCentroX = 680;
    private int sCentroY = 50;

    private enum Screen { START, DATA, MAP, EXTERNAL }
    private Screen currentScreen = Screen.START;
    private Screen previousScreen = Screen.START;

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        if (!glfwInit()) throw new IllegalStateException("GLFW init falhou");

        window = glfwCreateWindow(1280, 720, "Tela Inicial", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Falha ao criar janela");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        setup2D(); // inicializa 2D para tela inicial

        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            boolean pressed = action == GLFW_PRESS;

            switch (key) {
                case GLFW_KEY_ENTER -> {
                    if (pressed && currentScreen == Screen.START) currentScreen = Screen.DATA;
                }
                case GLFW_KEY_ESCAPE -> escStartPressed = pressed;
                case GLFW_KEY_UP -> upStartPressed = pressed;
                case GLFW_KEY_DOWN -> downStartPressed = pressed;
                case GLFW_KEY_A -> aStartPressed = pressed;
                case GLFW_KEY_D -> dStartPressed = pressed;
                case GLFW_KEY_C -> { if (pressed) cameraMode = (cameraMode + 1) % 3; }

                // Alterna entre DATA e EXTERNAL
                case GLFW_KEY_E -> {
                    if (pressed) {
                        if (currentScreen == Screen.DATA) {
                            currentScreen = Screen.EXTERNAL;
                            if (shader == null || models.isEmpty()) initExternalView();
                        } else if (currentScreen == Screen.EXTERNAL) {
                            currentScreen = Screen.DATA;
                        }
                    }
                }

                // Abre/fecha o mapa
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
                    if (pressed && enemy.isMissileWarning()) { // se houver warning
                        fbw.activateCountermeasure();
                        System.out.println("Activated");
                    }
                }
                case GLFW_KEY_K -> {
                	if (glfwGetKey(window, GLFW_KEY_K) == GLFW_PRESS) {
                	    showModel = !showModel;
                	    System.out.println("Modelo da HUD: " + (showModel ? "visível" : "oculto"));
                	    // Pequeno delay pra evitar múltiplos toggles por segurar a tecla
                	    try { Thread.sleep(200); } catch (InterruptedException e) {}
                	}
                }
            }
        });

        glEnable(GL_TEXTURE_2D);

        textRenderer = new Text();
        try { textRenderer.init(); }
        catch (IOException e) { throw new RuntimeException("Erro ao carregar fonte", e); }

        fbw = new FlyByWire();
        fbw.start();

        enemy = new Enemy(this, fbw);
        enemy.start();

        camera = new Camera();
        camera.setPosition(0f, 5f, 20f);
        
        Mundo mundo = new Mundo(10000f, 5000f, 10000f, fbw); // 10 km x 5 km x 10 km
        mundo.start();
        
        glColor3f(0.2f, 0.6f, 0.2f);
        
        Vector3f centro = new Vector3f(mundo.getTamanho()).mul(0.5f);
        mundo.getAviao().setPosicao(centro);

        // carrega texturas
        mapTextureId = loadTexture("src/img/EuropeMap.PNG");
        backgroundTextureId = loadBackgroundImage();

        scene = Assimp.aiImportFile(
                "src/models/sr71/sr71nochute.glb",
                Assimp.aiProcess_Triangulate | Assimp.aiProcess_FlipUVs | Assimp.aiProcess_GenNormals
        );

        if (scene == null || scene.mRootNode() == null) {
            throw new RuntimeException("Erro ao carregar modelo SR-71: " + Assimp.aiGetErrorString());
        } else {
            System.out.println("✅ Modelo SR-71 carregado com sucesso!");
            System.out.println("➡️ Meshes: " + scene.mNumMeshes());
            System.out.println("➡️ Materiais: " + scene.mNumMaterials());
        }
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

    private void setup3D(Camera camera) {
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

        glEnable(GL_DEPTH_TEST);
    }


    private void setup2D() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, 1280, 720, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glDisable(GL_DEPTH_TEST);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            if (escStartPressed) break;

            // Atualiza tela
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
        setup2D();
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glColor3f(1f, 1f, 1f);
        glBindTexture(GL_TEXTURE_2D, backgroundTextureId);
        glBegin(GL_QUADS);
        glTexCoord2f(0,1); glVertex2f(0,0);
        glTexCoord2f(1,1); glVertex2f(1280,0);
        glTexCoord2f(1,0); glVertex2f(1280,720);
        glTexCoord2f(0,0); glVertex2f(0,720);
        glEnd();

        glColor3f(0f,0f,0f);
        textRenderer.renderText("Pressione ENTER", 590, 360);
    }

    private void renderDataScreen() {
    	setupOverviewCamera();
    	glLoadIdentity();
    	glEnable(GL_DEPTH_TEST);
    	glDisable(GL_BLEND);
        glClearColor(0f, 0f, 0, 0f);
    	

        // Ajuste da câmera
        switch (cameraMode) {
            case 0 -> { glTranslatef(5f, -10f, -20f); glRotatef(90f, 1f, 0f, 0f); }
            case 1 -> glTranslatef(0f, -2f, -15f);
            case 2 -> { glRotatef(180f, 0f, 1f, 0f); glTranslatef(0f, -2f, -15f); }
        }
        glTranslatef(10f, -5f, 0f);

        if (showModel && scene != null) {
            glPushMatrix();
            glColor3f(1f, 1f, 1f);
            glScalef(0.4f, 0.4f, 0.4f);
            renderNode(scene.mRootNode());
            glPopMatrix();
        }

        // HUD separado, seguro
        renderHUD();
        drawPlayer();
        renderCountermeasureHUD();
        
        // Controles
        if (aStartPressed) fbw.climbAltitude(0.01);
        if (dStartPressed) fbw.decreaseAltitude(0.01);
        if (upStartPressed) fbw.throttleUp(0.1);
        if (downStartPressed) fbw.throttleDown(0.1);
    }


    private void renderMapScreen() {
    	glDisable(GL_CULL_FACE);
    	glDisable(GL_DEPTH_TEST);
    	glEnable(GL_BLEND);
    	glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    	glBindTexture(GL_TEXTURE_2D, 0);
    	
        // ---- Prepara 2D ----
        setup2D();                 // garante ortografia 2D
        glLoadIdentity();          // reseta matriz
        glDisable(GL_DEPTH_TEST);  // 2D não precisa de depth
        glEnable(GL_TEXTURE_2D);   // habilita texturas
        glEnable(GL_BLEND);        // habilita blending
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // ---- Desenha mapa ----
        glBindTexture(GL_TEXTURE_2D, mapTextureId);
        glColor3f(1f, 1f, 1f);

        glBegin(GL_QUADS);
            glTexCoord2f(0f, 0f); glVertex2f(100f, 100f);
            glTexCoord2f(1f, 0f); glVertex2f(1180f, 100f);
            glTexCoord2f(1f, 1f); glVertex2f(1180f, 620f);
            glTexCoord2f(0f, 1f); glVertex2f(100f, 620f);
        glEnd();

        glBindTexture(GL_TEXTURE_2D, 0); // “desativa” textura

        // ---- HUD / Texto ----
        glColor3f(1f, 1f, 1f);
        textRenderer.renderText("Mapa ativo - pressione E para voltar", 480, 60);

        // ---- Reset OpenGL ----
        glDisable(GL_BLEND);
    }

    // inicializa shaders, câmera e carrega todos os meshes da cena para uma lista "models"
    private void initExternalView() {
        try {
            System.out.println("Inicializando visão externa...");

            if (scene == null) {
                System.err.println("A scene Assimp não foi carregada ainda.");
                return;
            }

            String vertexSrc = FileUtils.loadFileAsString("src/res/shaders/vertex.glsl");
            String fragmentSrc = FileUtils.loadFileAsString("src/res/shaders/fragment.glsl");
            shader = new ShaderProgram(vertexSrc, fragmentSrc);

            camera = new Camera();
            camera.setPosition(0f, 5f, 15f);
            camera.lookAt(0f, 0f, 0f);

            // carrega todos os meshes da scene
            models.clear();
            models.addAll(Model.loadAllFromScene(scene)); 

            if (models.isEmpty()) {
                System.err.println("⚠️ Nenhum mesh encontrado no modelo SR-71!");
            } else {
                System.out.println("✅ Modelos carregados: " + models.size());
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Erro ao inicializar visão externa", e);
        }
    }

    private void renderExternal() {
        if (shader == null || camera == null || models.isEmpty()) return;

        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        // Posição do avião
        Vector3f planePos = new Vector3f(0f, (float)data.getAltitude() / 10f, 0f); // escala visível

        // --- Configuração da câmera ---
        float cameraHeight = Math.max(planePos.y + 20f, 30f); 
        camera.setPosition(planePos.x, cameraHeight, planePos.z + 80f);
        camera.lookAt(planePos.x, planePos.y, planePos.z);
        // --- Limpa tela ---
        glClearColor(0.53f, 0.81f, 0.92f, 1f); // céu azul
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        // --- Ativa 3D ---
        setup3D(camera);

        // --- Renderiza chão infinito com grid ---
        renderGround();

        // --- Renderiza avião ---
        Matrix4f modelMatrix = new Matrix4f()
                .translate(planePos)
                .rotateX((float)Math.toRadians(-90))
                .rotateZ((float)Math.toRadians(180))
                .rotateY((float)Math.toRadians(90))
                .rotateX((float)Math.toRadians(data.pitch))
                .rotateZ((float)Math.toRadians(data.roll))
                .scale(0.8f);

        shader.bind();
        shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
        shader.setUniformMatrix4f("view", camera.getViewMatrix());
        shader.setUniformMatrix4f("model", modelMatrix);

        for (Model m : models) {
            m.render();
        }

        shader.unbind();
    }
    
    private void renderHUD() {
    	
    	countermeasureActive = enemy.isMissileWarning();
    	
        // --- Configurações básicas de 2D ---
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBindTexture(GL_TEXTURE_2D, 0);

        setup2D();
        glLoadIdentity();

        FlyByWire.FlightData data = fbw.getLastData();
        
        // --- Informações do avião ---
        if (data != null) {
            textRenderer.renderText("Alt: " + data.getAltitude(), sEsquerdaX, sEsquerdaY);
            textRenderer.renderText("Pitch: " + data.getPitch(), sDireitaX + 20, sDireitaY);
            textRenderer.renderText("Roll: " + data.getRoll(), sCentroX, sCentroY);
            textRenderer.renderText("Speed: " + String.format("%.1f km/h", data.getSpeed()*3.6), sEsquerdaX, sEsquerdaY+30);
            textRenderer.renderText("MACH: " + data.getMach(), sEsquerdaX, sEsquerdaY+60);
            textRenderer.renderText("X: " + fbw.getPosicao().x, sDireitaX, sDireitaY + 40);
            textRenderer.renderText("Face: " + fbw.getDirecao().x, sDireitaX - 300, sDireitaY + 40);
        }

        // --- Barrinha do player ---
//        drawPlayer();

        // --- Barrinha verde / ACTIVATE ---
        if (countermeasureActive) {
            glColor3f(0f, 1f, 0f);
            glBegin(GL_QUADS);
            glVertex2f(countermeasureX, countermeasureY);
            glVertex2f(countermeasureX + 100, countermeasureY);
            glVertex2f(countermeasureX + 100, countermeasureY + 50);
            glVertex2f(countermeasureX, countermeasureY + 50);
            glEnd();

            glColor3f(1f, 1f, 1f);
            textRenderer.renderText("ACTIVATE", countermeasureX + 10, countermeasureY + 15);
        }

        // --- Incoming missile ---
        if (enemy.isMissileWarning()) { 
            glColor3f(1f, 0f, 0f);
            textRenderer.renderText("INCOMING MISSILE!", 500, 200);
            glColor3f(1f, 1f, 1f);
        }

        // --- Reset de cores pra evitar problemas ---
        glColor3f(1f, 1f, 1f);
    }

    private void drawPlayer() {
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        double altMin = 0, altMax = 85000;
        double normalized = (data.getAltitude() - altMin) / (altMax - altMin);
        if (normalized < 0) normalized = 0;
        if (normalized > 1) normalized = 1;

        float y = (float)(600f - normalized * (600f - 400f));

        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBindTexture(GL_TEXTURE_2D, 0);

        glLineWidth(2f);
        glColor3f(1f, 0f, 0f);

        glBegin(GL_LINES);
        glVertex2f(80f, y);
        glVertex2f(100f, y);

        glColor3f(1f, 1f, 1f);

        //BARRINHA DE CIMA
        glVertex2f(60f, 375);
        glVertex2f(120f, 375);

        //BARRINHA DE BAIXO
        glVertex2f(60f, 675);
        glVertex2f(120f, 675);

        //LADOS
        glVertex2f(60f, 375);
        glVertex2f(60f, 675);

        glVertex2f(120f, 375);
        glVertex2f(120f, 675);

        glEnd();

        glLineWidth(1f); // volta pro padrão
    }

    private void renderCountermeasureHUD() {
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glBindTexture(GL_TEXTURE_2D, 0);

        if (!countermeasureActive) return;

        // --- Nova posição ---
        float boxWidth = 120f;
        float boxHeight = 50f;
        float boxX = 1280f - boxWidth - 50f; // 50 px da borda direita
        float boxY = 720f - boxHeight - 80f; // 80 px da borda inferior

        // --- Desenha quadrado verde ---
        glColor3f(0f, 1f, 0f);
        glBegin(GL_QUADS);
        glVertex2f(boxX, boxY);
        glVertex2f(boxX + boxWidth, boxY);
        glVertex2f(boxX + boxWidth, boxY + boxHeight);
        glVertex2f(boxX, boxY + boxHeight);
        glEnd();

        // --- Texto ACTIVATE ---
        glColor3f(1f, 1f, 1f);
        textRenderer.renderText("ACTIVATE", boxX + 15f, boxY + 15f);

        glColor3f(1f, 1f, 1f); // reset
    }

    private void renderGround() {
        glPushMatrix();
        glColor3f(0.2f, 0.6f, 0.2f);

        float size = 200f; // extensão do grid
        float step = 10f;

        // obtém posição da câmera
        Vector3f camPos = camera.getPosition();

        glBegin(GL_LINES);
        // linhas paralelas ao eixo X
        for (float z = -size; z <= size; z += step) {
            glVertex3f(camPos.x - size, 0f, camPos.z + z);
            glVertex3f(camPos.x + size, 0f, camPos.z + z);
        }
        // linhas paralelas ao eixo Z
        for (float x = -size; x <= size; x += step) {
            glVertex3f(camPos.x + x, 0f, camPos.z - size);
            glVertex3f(camPos.x + x, 0f, camPos.z + size);
        }
        glEnd();

        glPopMatrix();
    }





    private void renderNode(AINode node) {
        IntBuffer meshIndicesBuf = node.mMeshes();
        if (meshIndicesBuf != null) {
            int[] meshIndices = new int[node.mNumMeshes()];
            meshIndicesBuf.get(meshIndices);
            for (int meshIndex : meshIndices) renderMesh(AIMesh.create(scene.mMeshes().get(meshIndex)));
        }

        PointerBuffer children = node.mChildren();
        for (int i = 0; i < node.mNumChildren(); i++) renderNode(AINode.create(children.get(i)));
    }

    private void renderMesh(AIMesh mesh) {
        AIVector3D.Buffer vertices = mesh.mVertices();
        AIVector3D.Buffer normals = mesh.mNormals();
        AIFace.Buffer faces = mesh.mFaces();

        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDisable(GL_BLEND);

        glBegin(GL_TRIANGLES);
        for (int i = 0; i < faces.remaining(); i++) {
            AIFace face = faces.get(i);
            IntBuffer indices = face.mIndices();
            for (int j = 0; j < indices.remaining(); j++) {
                int index = indices.get(j);
                if (normals != null && normals.remaining() > index) {
                    AIVector3D normal = normals.get(index);
                    glNormal3f(normal.x(), normal.y(), normal.z());
                } else glNormal3f(0,0,1);

                AIVector3D vertex = vertices.get(index);
                glVertex3f(vertex.x(), vertex.y(), vertex.z());
            }
        }
        glEnd();
    }

    private void setupOverviewCamera() {
        setup3D(camera); // só usa o setup3D existente
        glLoadIdentity();
        glTranslatef(0f, -5f, -20f);
        glRotatef(20f, 1f, 0f, 0f);
    }

    private void setupCockpitCamera() {
        setup3D(camera);
        glLoadIdentity();
        glTranslatef(0f, 0f, 0f);
        glRotatef(0f, 1f, 0f, 0f);
    }
    
    //testes										
    private void renderAxis() {
        glBegin(GL_LINES);

        // X vermelho
        glColor3f(1f, 0f, 0f);
        glVertex3f(0f, 0f, 0f);
        glVertex3f(10f, 0f, 0f);

        // Y verde
        glColor3f(0f, 1f, 0f);
        glVertex3f(0f, 0f, 0f);
        glVertex3f(0f, 10f, 0f);

        // Z azul
        glColor3f(0f, 0f, 1f);
        glVertex3f(0f, 0f, 0f);
        glVertex3f(0f, 0f, 10f);

        glEnd();
    }


    private void cleanup() {
        glDeleteTextures(mapTextureId);
        glDeleteTextures(backgroundTextureId);
        if (scene != null) Assimp.aiReleaseImport(scene);
        // limpar modelos / VBOs se necessário
        for (Model m : models) m.cleanup();
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

    public static void main(String[] args) { new FlyData().run(); }
}
