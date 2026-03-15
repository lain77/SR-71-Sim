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
import fbw.gameplay.FlightStats;
import fbw.gameplay.Mission;
import fbw.gameplay.MissionManager;
import fbw.gameplay.WeatherSystem;

public class FlyData {

    Audio audio = new Audio();
    private ShaderProgram shader;
    private SunBillboard sunBillboard;
    private ShaderProgram sunShader;
    private CloudSystem cloudSystem;
    
    private Framebuffer    sceneBuffer;
    private Framebuffer    brightBuffer;
    private Framebuffer    blurBufferH;
    private Framebuffer    blurBufferV;
    private PostProcessQuad postQuad;
    private ShaderProgram   bloomBrightShader;
    private ShaderProgram   bloomBlurShader;
    private ShaderProgram   finalPostShader;
    private ShaderProgram   heatHazeShader;
    private float           postTime = 0f;
    
    private Camera camera;
    private List<Model> models = new ArrayList<>(); 

    private boolean introMusicStarted = false;
    private int logoTextureId;
    
    private long window;
    private int mapTextureId;
    private int backgroundTextureId;

    private boolean paused = false;
    
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

    private FlightStats stats        = new FlightStats();
    private float       introTimer   = 0f;
    private float       logoTimer    = 0f;
    private boolean     introSkipped = false;
    private float       deathTimer   = 0f;

    // Ângulo da câmera cinemática da intro
    private float introCamAngle = 0f;
    
    private MissionManager missionManager;
    private WeatherSystem  weather;
    private long           lastFrameTime;
    
    private enum Screen {
        LOGO,       
        INTRO,    
        MAIN_MENU, 
        START,      
        DATA,
        MAP,
        EXTERNAL,
        MISSION_SELECT,
        DEBRIEFING,
        GAME_OVER
    }

    private Screen currentScreen  = Screen.LOGO;
    private Screen previousScreen = Screen.LOGO;
    
    private GroundModel ground;
    private int groundTextureId;
    
    private boolean gameplayStarted = false;
    
    private int menuSelectedIndex = 0;
    private int missionSelectedIndex = 0;
    private int pauseSelectedIndex = 0;
    
    private int rockTextureId;
    private int snowTextureId;
    
    private TreeSystem treeSystem;

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

        enemy = new Enemy(this, fbw);

        missionManager = new MissionManager();
        weather        = new WeatherSystem();
        lastFrameTime  = System.currentTimeMillis();
        
        Mundo mundo = new Mundo(10000f, 5000f, 10000f, fbw);
        mundo.start();
        Vector3f centro = new Vector3f(mundo.getTamanho()).mul(0.5f);
        mundo.getAviao().setPosicao(centro);

        logoTextureId = loadTexture("src/img/logoox.jpg");
        
        mapTextureId = loadTexture("src/img/EuropeMap.PNG");
        backgroundTextureId = loadBackgroundImage();

        groundTextureId = loadTexture("src/img/grass.jpg");
        
        rockTextureId = loadTexture("src/img/rock.jpg");
        snowTextureId = loadTexture("src/img/snow.jpg");
        
        ground = new GroundModel(400000f, 120f);
        
        init3DSystem();
    }

    private void startGameplay() {
        if (gameplayStarted) return; // evita iniciar duas vezes
        fbw.start();
        enemy.start();
        gameplayStarted = true;
    }

    private void stopGameplay() {
        if (!gameplayStarted) return;
        enemy.stop();
        fbw.stop();
        fbw.revive();
        fbw.setPosicao(new Vector3f(5000f, 2000f, 5000f));
        fbw.setThrottle(200);
        gameplayStarted = false;
    }
    
    private void init3DSystem() {
        try {
            System.out.println("initializing 3D engine");
            String vertexSrc = FileUtils.loadFileAsString("src/res/shaders/vertex.glsl");
            String fragmentSrc = FileUtils.loadFileAsString("src/res/shaders/fragment.glsl");
            shader = new ShaderProgram(vertexSrc, fragmentSrc);
            String sunVert = FileUtils.loadFileAsString("src/res/shaders/sun_vertex.glsl");
            String sunFrag = FileUtils.loadFileAsString("src/res/shaders/sun_fragment.glsl");
            sunShader    = new ShaderProgram(sunVert, sunFrag);
            sunBillboard = new SunBillboard();
            cloudSystem = new CloudSystem();
            treeSystem = new TreeSystem();
            
         // Framebuffers
            sceneBuffer  = new Framebuffer(1280, 720);
            brightBuffer = new Framebuffer(1280, 720);
            blurBufferH  = new Framebuffer(1280, 720);
            blurBufferV  = new Framebuffer(1280, 720);
            postQuad     = new PostProcessQuad();

            // Shaders de post
            String postVert = FileUtils.loadFileAsString("src/res/shaders/post_vertex.glsl");
            bloomBrightShader = new ShaderProgram(postVert,
                FileUtils.loadFileAsString("src/res/shaders/bloom_bright.glsl"));
            bloomBlurShader   = new ShaderProgram(postVert,
                FileUtils.loadFileAsString("src/res/shaders/bloom_blur.glsl"));
            finalPostShader   = new ShaderProgram(postVert,
                FileUtils.loadFileAsString("src/res/shaders/final_post.glsl"));
            heatHazeShader    = new ShaderProgram(postVert,
                FileUtils.loadFileAsString("src/res/shaders/heat_haze.glsl"));

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
            case GLFW_KEY_SPACE -> {
                if (pressed) {
                    if (currentScreen == Screen.LOGO)  { currentScreen = Screen.INTRO; logoTimer = 999f; }
                    if (currentScreen == Screen.INTRO) { introSkipped = true; currentScreen = Screen.MAIN_MENU; }
                }
            }
            case GLFW_KEY_ENTER -> {
                if (pressed) {
                    if (paused) { confirmPauseSelection(); break; }
                    if (currentScreen == Screen.LOGO)       currentScreen = Screen.INTRO;
                    if (currentScreen == Screen.INTRO)      { introSkipped = true; currentScreen = Screen.MAIN_MENU; }
                    if (currentScreen == Screen.MAIN_MENU)  confirmMenuSelection();
                    if (currentScreen == Screen.DEBRIEFING) currentScreen = Screen.MISSION_SELECT;
                    if (currentScreen == Screen.GAME_OVER)  { fbw.revive(); currentScreen = Screen.DEBRIEFING; }
                    if (currentScreen == Screen.MISSION_SELECT) {
                        selectAndStart(missionSelectedIndex);
                    }
                    if (currentScreen == Screen.DEBRIEFING) {
                        stopGameplay();
                        currentScreen = Screen.MISSION_SELECT;
                    }
                }
            }
            
            case GLFW_KEY_ESCAPE -> {
                if (pressed) {
                    if (currentScreen == Screen.EXTERNAL || currentScreen == Screen.DATA) {
                        paused = !paused; // toggle pausa
                    } else {
                        escStartPressed = true;
                    }
                }
            }
                case GLFW_KEY_UP -> {
                    if (!pressed) break;
                    if (paused) { pauseSelectedIndex = Math.max(0, pauseSelectedIndex - 1); break; }
                    if (currentScreen == Screen.MAIN_MENU)
                        menuSelectedIndex = Math.max(0, menuSelectedIndex - 1);
                    else if (currentScreen == Screen.MISSION_SELECT)
                        missionSelectedIndex = Math.max(0, missionSelectedIndex - 1);
                    else if (currentScreen == Screen.EXTERNAL)
                        fbw.setPitchInput(1);
                }
                case GLFW_KEY_DOWN -> {
                    if (!pressed) break;
                    if (paused) { pauseSelectedIndex = Math.min(2, pauseSelectedIndex + 1); break; }
                    if (currentScreen == Screen.MAIN_MENU)
                        menuSelectedIndex = Math.min(4, menuSelectedIndex + 1);
                    else if (currentScreen == Screen.MISSION_SELECT)
                        missionSelectedIndex = Math.min(missionManager.getMissions().size() - 1, missionSelectedIndex + 1);
                    else if (currentScreen == Screen.EXTERNAL)
                        fbw.setPitchInput(-1);
                }
                case GLFW_KEY_A    -> fbw.setRollInput(pressed ? -1 : 0);
                case GLFW_KEY_D    -> fbw.setRollInput(pressed ?  1 : 0);
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
                case GLFW_KEY_N -> {
                    if (pressed) {
                        if (currentScreen != Screen.MISSION_SELECT) {
                            previousScreen = currentScreen;
                            missionManager.openMenu();
                            currentScreen = Screen.MISSION_SELECT;
                        } else {
                            currentScreen = previousScreen; // volta de onde veio
                        }
                    }
                }
                case GLFW_KEY_1 -> { if (pressed) selectAndStart(0); }
                case GLFW_KEY_2 -> { if (pressed) selectAndStart(1); }
                case GLFW_KEY_3 -> { if (pressed) selectAndStart(2); }
                case GLFW_KEY_4 -> { if (pressed) selectAndStart(3); }
                case GLFW_KEY_5 -> { if (pressed) selectAndStart(4); }
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
            // Delta time em segundos
            long now   = System.currentTimeMillis();
            float delta = (now - lastFrameTime) / 1000f;
            boolean emVoo = currentScreen == Screen.EXTERNAL 
                    || currentScreen == Screen.DATA;

            // Checa morte
            if (emVoo && !paused) {
                stats.sample(fbw.getLastData());
                checkTerrainCollision();
                missionManager.update(delta, fbw, enemy);
                weather.update(delta, fbw);

                // Checa morte
                if (fbw.isDead()) {
                    deathTimer += delta;
                    if (deathTimer >= 2.5f) {
                        stats.setCompleted(false);
                        currentScreen = Screen.GAME_OVER;
                        deathTimer    = 0f;
                    }
                }

                // Checa missão completa
                Mission current = missionManager.currentMission();
                if (current != null && current.getState() == Mission.State.SUCCESS) {
                    stats.setCompleted(true);
                    currentScreen = Screen.DEBRIEFING;
                }
            }
            
            lastFrameTime = now;
            delta = Math.min(delta, 0.05f); // cap em 50ms para evitar pulos

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            if (escStartPressed) break;

            if (paused && emVoo) {
                renderPauseMenu();
            } else {
                switch (currentScreen) {
                case LOGO           -> renderLogo(delta);
                case INTRO          -> renderIntro(delta);
                case MAIN_MENU      -> renderMainMenu();
                case START          -> renderStartScreen();
                case DATA           -> renderDataScreen();
                case MAP            -> renderMapScreen();
                case EXTERNAL       -> renderExternal();
                case MISSION_SELECT -> renderMissionSelect();
                case DEBRIEFING     -> renderDebriefing();
                case GAME_OVER      -> renderGameOver();
            }
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

    private void renderMissionSelect() {
        setup2DLegado();
        glClearColor(0.02f, 0.02f, 0.05f, 1f);

        var missions = missionManager.getMissions();

        // ── Painel esquerdo: lista de missões ──────────────────────────
        glDisable(GL_TEXTURE_2D);
        glColor4f(0f, 0f, 0f, 0.7f);
        glBegin(GL_QUADS);
        glVertex2f(0,0); glVertex2f(420,0);
        glVertex2f(420,720); glVertex2f(0,720);
        glEnd();

        // Linha separadora
        glColor3f(0.5f, 0.05f, 0.05f);
        glLineWidth(2f);
        glBegin(GL_LINES);
        glVertex2f(420, 0); glVertex2f(420, 720);
        glEnd();
        glLineWidth(1f);

        glEnable(GL_TEXTURE_2D);
        glColor3f(0.6f, 0.6f, 0.6f);
        textRenderer.renderText("SELECAO DE MISSOES", 40, 50);
        glColor3f(0.3f, 0.3f, 0.3f);
        textRenderer.renderText("──────────────────────", 40, 68);

        for (int i = 0; i < missions.size(); i++) {
            var m = missions.get(i);
            float y = 110f + i * 90f;

            String statusTag = switch (m.getState()) {
                case WAITING -> "";
                case ACTIVE  -> "  [ATIVA]";
                case SUCCESS -> "  [OK]";
                case FAILED  -> "  [X]";
            };

            if (i == missionSelectedIndex) {
                // Destaque da selecionada
                glDisable(GL_TEXTURE_2D);
                glColor4f(0.6f, 0.05f, 0.05f, 0.4f);
                glBegin(GL_QUADS);
                glVertex2f(0, y - 4); glVertex2f(418, y - 4);
                glVertex2f(418, y + 56); glVertex2f(0, y + 56);
                glEnd();
                glEnable(GL_TEXTURE_2D);
                glColor3f(1f, 0.3f, 0.3f);
            } else {
                glColor3f(0.55f, 0.55f, 0.55f);
            }

            textRenderer.renderText((i + 1) + ". " + m.getName() + statusTag, 30, y);

            // Tipo da missão em menor
            glColor3f(i == missionSelectedIndex ? 0.8f : 0.35f, 0.35f, 0.35f);
            String tipo = m.getClass().getSimpleName()
                .replace("Mission", "")
                .replace("Recon",   "Reconhecimento")
                .replace("Escape",  "Fuga de Missil")
                .replace("FlightWindow", "Janela de Voo");
            textRenderer.renderText("   " + tipo, 30, y + 22);
        }

        // ── Painel direito: briefing da missão selecionada ────────────
        var sel = missions.get(missionSelectedIndex);

        glEnable(GL_TEXTURE_2D);
        glColor3f(0.8f, 0.8f, 0.8f);
        textRenderer.renderText(sel.getName(), 450, 80);

        glColor3f(0.3f, 0.3f, 0.3f);
        glDisable(GL_TEXTURE_2D);
        glBegin(GL_LINES);
        glVertex2f(450, 100); glVertex2f(1240, 100);
        glEnd();
        glEnable(GL_TEXTURE_2D);

        glColor3f(0.55f, 0.75f, 0.55f);
        textRenderer.renderText("BRIEFING:", 450, 120);
        glColor3f(0.7f, 0.7f, 0.7f);

        String[] lines = sel.getBriefing().split("\n");
        for (int l = 0; l < lines.length; l++) {
            textRenderer.renderText(lines[l], 450, 145 + l * 22);
        }

        // Status da missão selecionada
        float statusY = 420;
        glColor3f(0.4f, 0.4f, 0.4f);
        glDisable(GL_TEXTURE_2D);
        glBegin(GL_LINES);
        glVertex2f(450, statusY); glVertex2f(1240, statusY);
        glEnd();
        glEnable(GL_TEXTURE_2D);

        String statusText = switch (sel.getState()) {
            case WAITING -> "Pronta para iniciar";
            case ACTIVE  -> "Em andamento";
            case SUCCESS -> "Concluida com sucesso";
            case FAILED  -> "Falhou — pode tentar novamente";
        };
        float[] statusColor = switch (sel.getState()) {
            case WAITING -> new float[]{0.6f, 0.6f, 0.6f};
            case ACTIVE  -> new float[]{1f, 1f, 0f};
            case SUCCESS -> new float[]{0.2f, 1f, 0.3f};
            case FAILED  -> new float[]{1f, 0.2f, 0.2f};
        };
        glColor3f(statusColor[0], statusColor[1], statusColor[2]);
        textRenderer.renderText("STATUS: " + statusText, 450, statusY + 20);

        // Rodapé
        glColor3f(0.3f, 0.3f, 0.3f);
        textRenderer.renderText("SETAS = Navegar   ENTER = Iniciar   N = Voltar", 450, 680);
    }
    
    private void renderDataScreen() {
        glClearColor(0.04f, 0.04f, 0.06f, 1f);
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        // ── 1. MODELO 3D (igual ao antes) ────────────────────────────
        if (showModel) {
            glEnable(GL_DEPTH_TEST);
            Vector3f origin = new Vector3f(0, 0, 0);
            camera.setMode(cameraMode, origin);

            Matrix4f modelMatrix = new Matrix4f()
                .translate(0f, -2f, -10f)
                .rotateX((float)Math.toRadians(-90))
                .rotateZ((float)Math.toRadians(180))
                .rotateY((float)Math.toRadians(90))
                .rotateX((float)Math.toRadians(data.pitch))
                .rotateZ((float)Math.toRadians(data.roll))
                .scale(0.3f);

            shader.bind();
            shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
            shader.setUniformMatrix4f("view",       camera.getViewMatrix());
            shader.setUniformMatrix4f("model",      modelMatrix);
            shader.setUniform3f("objectColor", 0.4f, 0.4f, 0.4f);
            shader.setUniform3f("lightPos",    0f, 100f, 0f);
            shader.setUniform3f("lightColor",  1.0f, 1.0f, 1.0f);
            shader.setUniform3f("viewPos",     camera.getPosition().x,
                                               camera.getPosition().y,
                                               camera.getPosition().z);
            // Uniforms obrigatórios do novo fragment shader
            shader.setUniform1i("useTexture",  0);
            shader.setUniform1i("useTerrain",  0);
            shader.setUniform3f("lightDir",   -0.5f, -1.0f, -0.3f);
            shader.setUniform3f("skyColor",    0.04f, 0.04f, 0.06f);
            shader.setUniform3f("rimColor",    0.3f, 0.5f, 1.0f);
            shader.setUniformFloat("rimStrength",      0.5f);
            shader.setUniformFloat("fogDensity",       0.0f);
            shader.setUniform3f("emissiveColor",       0f, 0f, 0f);
            shader.setUniformFloat("emissiveStrength", 0f);
            shader.setUniform3f("sunDir",      0.4f, 0.8f, 0.3f);

            for (Model m : models) m.render();
            shader.unbind(); // ESSENCIAL — sem isso quebra o 2D legado
        }

        // ── 2. HUD 2D ─────────────────────────────────────────────────
        setup2DLegado();
        glEnable(GL_TEXTURE_2D);

        // Horizonte artificial
        drawArtificialHorizon(640, 360, data);

        // Radar tático
        drawRadar(1100, 580, 140);

        // Estado dos sistemas
        drawSystemsPanel(data);

        // Gráfico de velocidade
        drawSpeedGraph(50, 400, data);

        // HUD principal + altímetro
        renderHUD();
        renderCountermeasureHUD();

        // G-force
        drawGForce();
    }

    // ── HORIZONTE ARTIFICIAL ──────────────────────────────────────────
    private void drawArtificialHorizon(float cx, float cy, FlyByWire.FlightData data) {
        setup2DLegado();
        glDisable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);

        float size  = 100f;
        float pitch = (float) data.pitch;
        float roll  = (float) data.roll;

        // Fundo preto
        glColor3f(0f, 0f, 0f);
        glBegin(GL_QUADS);
        glVertex2f(cx - size, cy - size);
        glVertex2f(cx + size, cy - size);
        glVertex2f(cx + size, cy + size);
        glVertex2f(cx - size, cy + size);
        glEnd();

        // Linha do horizonte rotacionada pelo roll + deslocada pelo pitch
        float pitchOffset = pitch * 3f;
        float rollRad     = (float) Math.toRadians(roll);
        float cosR        = (float) Math.cos(rollRad);
        float sinR        = (float) Math.sin(rollRad);

        // Metade de cima = céu (azul)
        glColor3f(0.2f, 0.5f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(cx - size, cy - size);
        glVertex2f(cx + size, cy - size);
        glVertex2f(cx + size, cy + pitchOffset);
        glVertex2f(cx - size, cy + pitchOffset);
        glEnd();

        // Metade de baixo = terra (marrom)
        glColor3f(0.4f, 0.25f, 0.1f);
        glBegin(GL_QUADS);
        glVertex2f(cx - size, cy + pitchOffset);
        glVertex2f(cx + size, cy + pitchOffset);
        glVertex2f(cx + size, cy + size);
        glVertex2f(cx - size, cy + size);
        glEnd();

        // Linha do horizonte inclinada
        glColor3f(1f, 1f, 1f);
        glLineWidth(2f);
        glBegin(GL_LINES);
        glVertex2f(cx - size * cosR - pitchOffset * sinR,
                   cy + pitchOffset * cosR - size * sinR);
        glVertex2f(cx + size * cosR + pitchOffset * sinR,
                   cy - pitchOffset * cosR + size * sinR);
        glEnd();

        // Marca central (avião)
        glColor3f(1f, 1f, 0f);
        glLineWidth(2f);
        glBegin(GL_LINES);
        glVertex2f(cx - 30, cy); glVertex2f(cx - 10, cy);
        glVertex2f(cx - 10, cy); glVertex2f(cx - 10, cy + 8);
        glVertex2f(cx + 10, cy); glVertex2f(cx + 30, cy);
        glVertex2f(cx + 10, cy); glVertex2f(cx + 10, cy + 8);
        glEnd();
        glLineWidth(1f);

        // Borda circular
        glColor3f(0.6f, 0.6f, 0.6f);
        glLineWidth(1.5f);
        int segs = 48;
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < segs; i++) {
            double a = Math.PI * 2 * i / segs;
            glVertex2f(cx + (float) Math.cos(a) * size, cy + (float) Math.sin(a) * size);
        }
        glEnd();
        glLineWidth(1f);

        // Label
        glEnable(GL_TEXTURE_2D);
        glColor3f(0.6f, 0.6f, 0.6f);
        textRenderer.renderText("ATITUDE", cx - 28, cy - size - 15);
    }

    // ── RADAR TÁTICO ──────────────────────────────────────────────────
    private void drawRadar(float cx, float cy, float radius) {
        setup2DLegado();
        glDisable(GL_TEXTURE_2D);

        // Fundo escuro
        glColor3f(0f, 0.08f, 0f);
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(cx, cy);
        for (int i = 0; i <= 64; i++) {
            double a = Math.PI * 2 * i / 64;
            glVertex2f(cx + (float)Math.cos(a) * radius, cy + (float)Math.sin(a) * radius);
        }
        glEnd();

        // Círculos de alcance
        glColor3f(0f, 0.35f, 0f);
        glLineWidth(0.5f);
        for (float r : new float[]{radius * 0.33f, radius * 0.66f, radius}) {
            glBegin(GL_LINE_LOOP);
            for (int i = 0; i < 64; i++) {
                double a = Math.PI * 2 * i / 64;
                glVertex2f(cx + (float)Math.cos(a) * r, cy + (float)Math.sin(a) * r);
            }
            glEnd();
        }

        // Linhas de grade
        glBegin(GL_LINES);
        glVertex2f(cx - radius, cy); glVertex2f(cx + radius, cy);
        glVertex2f(cx, cy - radius); glVertex2f(cx, cy + radius);
        glEnd();

        // Avião no centro
        glColor3f(0f, 1f, 0.3f);
        glPointSize(5f);
        glBegin(GL_POINTS);
        glVertex2f(cx, cy);
        glEnd();

        // Inimigo (se existir)
        if (enemy != null) {
            Vector3f myPos  = fbw.getPosicao();
            Vector3f enyPos = enemy.getPosition(); // precisa existir no Enemy
            if (enyPos != null) {
                float scale = radius / 50000f; // 50km de alcance do radar
                float ex = cx + (enyPos.x - myPos.x) * scale;
                float ey = cy + (enyPos.z - myPos.z) * scale;

                // Só desenha se estiver dentro do radar
                if (Math.hypot(ex - cx, ey - cy) < radius) {
                    glColor3f(1f, 0.2f, 0.2f);
                    glPointSize(6f);
                    glBegin(GL_POINTS);
                    glVertex2f(ex, ey);
                    glEnd();
                }
            }
        }

        glPointSize(1f);
        glLineWidth(1f);

        glEnable(GL_TEXTURE_2D);
        glColor3f(0f, 0.8f, 0.2f);
        textRenderer.renderText("RADAR", cx - 18, cy - radius - 15);
    }

    // ── SISTEMAS ──────────────────────────────────────────────────────
    private void drawSystemsPanel(FlyByWire.FlightData data) {
        setup2DLegado();
        float px = 50, py = 500;

        glEnable(GL_TEXTURE_2D);
        glColor3f(0.5f, 0.5f, 0.5f);
        textRenderer.renderText("SISTEMAS", px, py);

        // Barras de status
        drawBar(px, py + 25, 160, 14, (float)(fbw.getEngineTemp() / 100.0),
                "ENG TEMP", 0.2f, 0.8f, 0.2f, 0.9f, 0.1f, 0.1f);

        drawBar(px, py + 50, 160, 14, (float)(fbw.getFuel() / 100000.0),
                "FUEL", 0.2f, 0.6f, 1.0f, 1.0f, 0.5f, 0.0f);

        drawBar(px, py + 75, 160, 14, (float)(fbw.getThrottle() / 6000.0),
                "THROTTLE", 0.3f, 0.9f, 0.3f, 0.9f, 0.3f, 0.3f);

        // Mach em destaque
        glColor3f(0f, 1f, 0.5f);
        textRenderer.renderText(String.format("MACH %.2f", data.mach), px, py + 105);
    }

    private void drawBar(float x, float y, float w, float h,
                         float fill, String label,
                         float r1, float g1, float b1,
                         float r2, float g2, float b2) {
        glDisable(GL_TEXTURE_2D);
        fill = Math.max(0, Math.min(1, fill));

        // Fundo
        glColor3f(0.15f, 0.15f, 0.15f);
        glBegin(GL_QUADS);
        glVertex2f(x, y); glVertex2f(x + w, y);
        glVertex2f(x + w, y + h); glVertex2f(x, y + h);
        glEnd();

        // Preenchimento com cor que muda conforme o nível
        float r = r1 + (r2 - r1) * fill;
        float g = g1 + (g2 - g1) * fill;
        float b = b1 + (b2 - b1) * fill;
        glColor3f(r, g, b);
        glBegin(GL_QUADS);
        glVertex2f(x, y); glVertex2f(x + w * fill, y);
        glVertex2f(x + w * fill, y + h); glVertex2f(x, y + h);
        glEnd();

        glEnable(GL_TEXTURE_2D);
        glColor3f(0.8f, 0.8f, 0.8f);
        textRenderer.renderText(label, x + w + 6, y + 2);
    }

    // ── GRÁFICO DE VELOCIDADE ─────────────────────────────────────────
    private void drawSpeedGraph(float x, float y, FlyByWire.FlightData data) {
        setup2DLegado();
        glDisable(GL_TEXTURE_2D);

        float w = 200, h = 80;

        // Fundo
        glColor3f(0.05f, 0.05f, 0.08f);
        glBegin(GL_QUADS);
        glVertex2f(x, y); glVertex2f(x + w, y);
        glVertex2f(x + w, y + h); glVertex2f(x, y + h);
        glEnd();

        // Grade
        glColor3f(0.2f, 0.2f, 0.2f);
        glBegin(GL_LINES);
        glVertex2f(x,     y + h / 2); glVertex2f(x + w, y + h / 2);
        glVertex2f(x + w / 2, y);     glVertex2f(x + w / 2, y + h);
        glEnd();

        // Linha do gráfico
        double[] hist  = fbw.getSpeedHistory();
        int      start = fbw.getHistoryIndex();
        glColor3f(0.2f, 0.9f, 0.5f);
        glLineWidth(1.5f);
        glBegin(GL_LINE_STRIP);
        for (int i = 0; i < 60; i++) {
            double spd  = hist[(start + i) % 60];
            float  px2  = x + (i / 59f) * w;
            float  py2  = y + h - (float)(spd / 6000.0) * h;
            glVertex2f(px2, py2);
        }
        glEnd();
        glLineWidth(1f);

        // Borda
        glColor3f(0.4f, 0.4f, 0.4f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y); glVertex2f(x + w, y);
        glVertex2f(x + w, y + h); glVertex2f(x, y + h);
        glEnd();

        glEnable(GL_TEXTURE_2D);
        glColor3f(0.5f, 0.5f, 0.5f);
        textRenderer.renderText("VELOCIDADE (60s)", x, y - 14);
    }

    // ── G-FORCE ───────────────────────────────────────────────────────
    private void drawGForce() {
        setup2DLegado();
        double g = fbw.getGForce();

        // Vinheta vermelha em G alto
        if (g > 4.0) {
            float intensity = (float) Math.min((g - 4.0) / 5.0, 0.6);
            glDisable(GL_TEXTURE_2D);
            glColor4f(0.8f, 0f, 0f, intensity);
            glEnable(GL_BLEND);

            // Bordas da tela ficam vermelhas
            glBegin(GL_QUADS);
            glVertex2f(0, 0);    glVertex2f(1280, 0);
            glVertex2f(1280, 80); glVertex2f(0, 80);
            glEnd();
            glBegin(GL_QUADS);
            glVertex2f(0, 640);    glVertex2f(1280, 640);
            glVertex2f(1280, 720); glVertex2f(0, 720);
            glEnd();
        }

        glEnable(GL_TEXTURE_2D);
        float gColor = g > 5 ? 1f : (g > 3 ? 1f : 0.6f);
        float gGreen = g > 5 ? 0f : (g > 3 ? 0.5f : 1f);
        glColor3f(gColor, gGreen, 0f);
        textRenderer.renderText(String.format("%.1fG", g), 620, 680);
    }

    private void renderExternal() {
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;
        renderWithPostProcess(0.016f);
    }
    
    private void renderExternalScene() {
        FlyByWire.FlightData data = fbw.getLastData();

        glClearColor(0.15f, 0.45f, 0.85f, 1f); 
        glEnable(GL_DEPTH_TEST);

        float lx = 0.5f, ly = 0.7f, lz = 0.2f;
        Vector3f posReal = fbw.getPosicao();

        Vector3f planePos = new Vector3f(posReal.x, (float)data.getAltitude() / 10f, posReal.z);
        
        camera.setMode(2, planePos); // Modo externa

        shader.bind();
        shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
        shader.setUniformMatrix4f("view", camera.getViewMatrix());

        // MOVE viewPos PARA CÁ — antes de qualquer coisa
        shader.setUniform3f("viewPos", camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
        shader.setUniform3f("lightDir", -lx, -ly, -lz); // mais horizontal
        shader.setUniform3f("lightColor",  2.0f,  1.8f,  1.4f); // mais forte para compensar ambient baixo
        shader.setUniform3f("skyColor", 0.15f, 0.45f, 0.85f);
        shader.setUniform3f("rimColor",    0.5f,  0.7f,  1.0f);
        shader.setUniformFloat("rimStrength", 1.2f);
        shader.setUniformFloat("fogDensity", 0.000000000012f);

        // Chão
        shader.setUniform1i("useTexture", 1);
        shader.setUniform1i("useTerrain", 1);
        shader.setUniform1i("texture1",    0);  // slot 0 = grama
        shader.setUniform1i("texRock",     1);  // slot 1 = rocha
        shader.setUniform1i("texSnow",     2);  // slot 2 = neve

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, groundTextureId);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, rockTextureId);
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, snowTextureId);
        
        // O chão fica cravado na posição 0,0,0 do mundo
        Matrix4f groundMatrix = new Matrix4f().translate(0, 0, 0); 
        shader.setUniformMatrix4f("model", groundMatrix);
        
        ground.render();
        glBindTexture(GL_TEXTURE_2D, 0);

        shader.setUniform1i("useTerrain", 0); // desliga blending do terreno
        glActiveTexture(GL_TEXTURE0);         // volta ao slot padrão

        // --- 2. DESENHAR O AVIÃO (SR-71) ---
        shader.setUniform1i("useTexture", 0);
        shader.setUniform3f("objectColor", 0.3f, 0.3f, 0.32f);
        shader.setUniform3f("lightPos", planePos.x, planePos.y + 500f, planePos.z); 
        shader.setUniform3f("lightColor", 1.0f, 1.0f, 1.0f);

        shader.setUniform3f("emissiveColor",       0f, 0f, 0f);
        shader.setUniformFloat("emissiveStrength", 0f);
        
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
        
        renderSun();
        renderClouds();
        
        FlyByWire.FlightData data2 = fbw.getLastData();
        if (data2 != null) {
            // Shader já está unbound aqui — rebinda para as árvores
            shader.bind();
            shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
            shader.setUniformMatrix4f("view",       camera.getViewMatrix());
            shader.setUniform1i("useTexture",  0);
            shader.setUniform1i("useTerrain",  0);
            shader.setUniform3f("lightDir",   -0.5f, -0.7f, -0.2f);
            shader.setUniform3f("lightColor",  2.0f,  1.8f,  1.4f);
            shader.setUniform3f("viewPos",     camera.getPosition().x,
                                               camera.getPosition().y,
                                               camera.getPosition().z);
            shader.setUniform3f("rimColor",    0.3f, 0.6f, 0.2f);
            shader.setUniformFloat("rimStrength",      0.3f);
            shader.setUniformFloat("fogDensity",       0.000000000012f);
            shader.setUniform3f("emissiveColor",       0f, 0f, 0f);
            shader.setUniformFloat("emissiveStrength", 0f);
            shader.setUniform3f("sunDir",      0.5f, 0.7f, 0.2f);
            shader.setUniform3f("skyColor",    0.15f, 0.45f, 0.85f);

            treeSystem.render(camera, shader);
            shader.unbind();
        }

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
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
            textRenderer.renderText(String.format("MACH: %.2f", data.getMach()), sEsquerdaX, sEsquerdaY + 60);
            textRenderer.renderText("X: " + fbw.getPosicao().x, sDireitaX, sDireitaY + 40);
            textRenderer.renderText("Face: " + fbw.getDirecao().x, sDireitaX - 300, sDireitaY + 40);
        }

        if (enemy.isMissileWarning()) {
            glDisable(GL_TEXTURE_2D);
            // Fundo vermelho no topo
            glColor4f(0.7f, 0f, 0f, 0.85f);
            glBegin(GL_QUADS);
            glVertex2f(400, 0); glVertex2f(880, 0);
            glVertex2f(880, 28); glVertex2f(400, 28);
            glEnd();
            glEnable(GL_TEXTURE_2D);
            glColor3f(1f, 1f, 1f);
            textRenderer.renderText("INCOMING MISSILE! — PRESS S", 430, 8);
            glColor3f(1f, 1f, 1f);
        }
     // No final do renderHUD(), antes de fechar:
        Mission current = missionManager.currentMission();
        if (current != null && current.getState() == Mission.State.ACTIVE) {
            glColor3f(1f, 1f, 0f);
            textRenderer.renderText(current.getHudStatus(), 400, 680);
            glColor3f(1f, 1f, 1f);
        }

        String weatherLabel = weather.getHudLabel();
        if (!weatherLabel.isEmpty()) {
            glColor3f(1f, 0.5f, 0f);
            textRenderer.renderText(weatherLabel, 500, 650);
            glColor3f(1f, 1f, 1f);
        }

        textRenderer.renderText("N = Missoes", 50, sEsquerdaY + 90);
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
        glDisable(GL_TEXTURE_2D);

        // Fundo escuro pequeno embaixo do radar
        glColor4f(0f, 0.4f, 0f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(960, 695); glVertex2f(1270, 695);
        glVertex2f(1270, 718); glVertex2f(960, 718);
        glEnd();

        glEnable(GL_TEXTURE_2D);
        glColor3f(0.3f, 1f, 0.3f);
        textRenderer.renderText("[ S ] LANÇAR CONTRA-MEDIDA", 968, 703);
        glColor3f(1f, 1f, 1f);
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
    
    private void renderClouds() {
        Vector3f sunDir = new Vector3f(-0.5f, -1.0f, -0.3f).normalize().negate();
        cloudSystem.render(camera, sunDir);
    }

    private void cleanup() {
        glDeleteTextures(mapTextureId);
        glDeleteTextures(backgroundTextureId);
        if (scene != null) Assimp.aiReleaseImport(scene);
        for (Model m : models) m.cleanup();
        if (shader != null) shader.cleanup();
        if (sunShader    != null) sunShader.cleanup();
        if (sunBillboard != null) sunBillboard.cleanup();
        if (cloudSystem != null) cloudSystem.cleanup();
        if (sceneBuffer  != null) sceneBuffer.cleanup();
        if (brightBuffer != null) brightBuffer.cleanup();
        if (blurBufferH  != null) blurBufferH.cleanup();
        if (blurBufferV  != null) blurBufferV.cleanup();
        if (postQuad     != null) postQuad.cleanup();
        if (bloomBrightShader != null) bloomBrightShader.cleanup();
        if (bloomBlurShader   != null) bloomBlurShader.cleanup();
        if (finalPostShader   != null) finalPostShader.cleanup();
        if (heatHazeShader    != null) heatHazeShader.cleanup();
        if (treeSystem != null) treeSystem.cleanup();
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
    
    private void renderSun() {
        // Direção do sol (mesma do lightDir, invertida)
        org.joml.Vector3f lightDirVec = new org.joml.Vector3f(-0.5f, -1.0f, -0.3f).normalize();
        org.joml.Vector3f sunDirection = new org.joml.Vector3f(0.5f, 0.7f, 0.2f).normalize();
        org.joml.Vector3f camPos = camera.getPosition();
        org.joml.Vector3f sunPos = new org.joml.Vector3f(camPos).add(
            new org.joml.Vector3f(sunDirection).mul(90000f)
        );

        // Vetores right e up da câmera para o billboard
        org.joml.Matrix4f viewMat = camera.getViewMatrix();
        org.joml.Vector3f camRight = new org.joml.Vector3f(viewMat.m00(), viewMat.m10(), viewMat.m20());
        org.joml.Vector3f camUp    = new org.joml.Vector3f(viewMat.m01(), viewMat.m11(), viewMat.m21());

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE); // additive blend — deixa o sol "explodir" de luz
        glDepthMask(false);                // não escreve no depth buffer

        sunShader.bind();
        sunShader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
        sunShader.setUniformMatrix4f("view", viewMat);
        sunShader.setUniform3f("center",   sunPos.x,   sunPos.y,   sunPos.z);
        sunShader.setUniform3f("camRight", camRight.x, camRight.y, camRight.z);
        sunShader.setUniform3f("camUp",    camUp.x,    camUp.y,    camUp.z);
        sunShader.setUniformFloat("size",  8000f); // tamanho do disco

        sunBillboard.render();
        sunShader.unbind();

        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA); // volta ao blend normal

        // Passa sunDir para o shader principal (scattering atmosférico)
        shader.bind();
        shader.setUniform3f("sunDir", sunDirection.x, sunDirection.y, sunDirection.z);
        shader.unbind();
    }
    
    private void selectAndStart(int index) {
        missionManager.selectMission(index);
        Mission m = missionManager.currentMission();
        stats.begin(m != null ? m.getName() : "Voo Livre");
        startGameplay();
        currentScreen = Screen.EXTERNAL;
    }
    
    private void checkTerrainCollision() {
        if (fbw.isDead()) return;
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        Vector3f pos        = fbw.getPosicao();
        float    groundY    = GroundModel.generateHeight(pos.x, pos.z);
        float    planeY     = (float)(data.altitude / 10f); // mesma escala do renderExternal

        if (planeY <= groundY + 5f) { // 5 unidades de margem
            fbw.kill();
            System.out.println("Colisão com terreno!");
        }
    }
    
    private void renderLogo(float delta) {
    	if (!introMusicStarted) {
    	audio.playSound("/audio/introjoi.wav"); // exporta o áudio da edit como WAV
        introMusicStarted = true;
}
        logoTimer += delta;
        setup2DLegado();

        // Fundo preto para o fade funcionar
        glClearColor(0f, 0f, 0f, 1f);

        float alpha;
        if      (logoTimer < 1.0f) alpha = logoTimer;
        else if (logoTimer < 2.5f) alpha = 1.0f;
        else if (logoTimer < 3.5f) alpha = 1.0f - (logoTimer - 2.5f);
        else { currentScreen = Screen.INTRO; return; }
        alpha = Math.max(0, Math.min(1, alpha));

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1f, 1f, 1f, alpha);
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, logoTextureId);

        // Preenche a tela toda — a imagem já tem o fundo certo
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(0,    0);
        glTexCoord2f(1, 0); glVertex2f(1280, 0);
        glTexCoord2f(1, 1); glVertex2f(1280, 720);
        glTexCoord2f(0, 1); glVertex2f(0,    720);
        glEnd();
        glBindTexture(GL_TEXTURE_2D, 0);

        // Skip discreto
        if (logoTimer > 1.0f) {
            glColor4f(0.3f, 0.3f, 0.3f, alpha * 0.5f);
            textRenderer.renderText("SPACE para pular", 575, 690);
        }
    }
    
    private void renderIntro(float delta) {
//        if (!introMusicStarted) {
//            audio.playSound("/audio/audioox.wav"); // exporta o áudio da edit como WAV
//            introMusicStarted = true;
//        }
        
        introTimer += delta;
        introCamAngle += delta * 18f; // câmera orbita lentamente

        // Pula após 8 segundos ou se skipado
        if (introTimer >= 8f || introSkipped) {
            audio.stopSound();
            introMusicStarted = false;
            currentScreen  = Screen.MAIN_MENU;
            introSkipped   = false;
            introTimer     = 0f;
            return;
        }

        // Fundo escuro (amanhecer)
        float dayBlend = Math.min(introTimer / 6f, 1f);
        glClearColor(
            0.02f + dayBlend * 0.51f,
            0.02f + dayBlend * 0.79f,
            0.05f + dayBlend * 0.87f,
            1f
        );
        glEnable(GL_DEPTH_TEST);

        // Câmera orbita o avião
        Vector3f origin = new Vector3f(0, 0, 0);
        float camX = (float)(Math.cos(Math.toRadians(introCamAngle)) * 20f);
        float camZ = (float)(Math.sin(Math.toRadians(introCamAngle)) * 20f);
        camera.setIntroCam(new Vector3f(camX, 4f, camZ), origin);

        Matrix4f modelMatrix = new Matrix4f()
            .translate(0f, 0f, 0f)
            .rotateX((float)Math.toRadians(-90))
            .rotateZ((float)Math.toRadians(270))
            .scale(0.8f);

        shader.bind();
        shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
        shader.setUniformMatrix4f("view",       camera.getViewMatrix());
        shader.setUniformMatrix4f("model",      modelMatrix);
        shader.setUniform1i("useTexture",  0);
        shader.setUniform1i("useTerrain",  0);
        shader.setUniform3f("objectColor", 0.12f, 0.12f, 0.12f);
        shader.setUniform3f("lightDir",   -0.5f, -1.0f, -0.3f);
        shader.setUniform3f("lightColor",  1.0f,  0.95f, 0.8f);
        shader.setUniform3f("skyColor",    0.02f + dayBlend * 0.51f,
                                           0.02f + dayBlend * 0.79f,
                                           0.05f + dayBlend * 0.87f);
        shader.setUniform3f("viewPos",     camX, 4f, camZ);
        shader.setUniform3f("rimColor",    0.4f, 0.6f, 1.0f);
        shader.setUniformFloat("rimStrength", 0.8f);
        shader.setUniformFloat("fogDensity",  0.0f);
        shader.setUniform3f("emissiveColor", 0f, 0f, 0f);
        shader.setUniformFloat("emissiveStrength", 0f);
        
        shader.setUniform3f("sunDir", 0.4f, 0.8f, 0.3f);
        
        for (Model m : models) m.render();
        shader.unbind();

        // Textos cinemáticos com fade
        setup2DLegado();
        glEnable(GL_TEXTURE_2D);

        // Título aparece após 1s
        if (introTimer > 1f) {
            float a = Math.min((introTimer - 1f) / 1.5f, 1f);
            glColor4f(1f, 1f, 1f, a);
            textRenderer.renderText("SR-71  BLACKBIRD", 430, 580);
        }
        // Subtítulo
        if (introTimer > 2.5f) {
            float a = Math.min((introTimer - 2.5f) / 1f, 1f);
            glColor4f(0.6f, 0.6f, 0.6f, a);
            textRenderer.renderText("Classified. Untouchable. Unstoppable.", 370, 610);
        }

        glColor3f(0.4f, 0.4f, 0.4f);
        textRenderer.renderText("SPACE para pular", 560, 700);
        glColor3f(1f, 1f, 1f);
    }
    
    private void renderMainMenu() {
        introCamAngle += 0.3f; // rotação lenta constante

        // Fundo com SR-71 girando
        glClearColor(0.02f, 0.02f, 0.04f, 1f);
        glEnable(GL_DEPTH_TEST);

        Vector3f origin = new Vector3f(0, 0, 0);
        float camX = (float)(Math.cos(Math.toRadians(introCamAngle)) * 22f);
        float camZ = (float)(Math.sin(Math.toRadians(introCamAngle)) * 22f);
        camera.setIntroCam(new Vector3f(camX, 5f, camZ), origin);

        Matrix4f modelMatrix = new Matrix4f()
            .rotateX((float)Math.toRadians(-90))
            .rotateZ((float)Math.toRadians(270))
            .scale(0.8f);

        shader.bind();
        shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
        shader.setUniformMatrix4f("view",       camera.getViewMatrix());
        shader.setUniformMatrix4f("model",      modelMatrix);
        shader.setUniform1i("useTexture",  0);
        shader.setUniform1i("useTerrain",  0);
        shader.setUniform3f("objectColor", 0.12f, 0.12f, 0.14f);
        shader.setUniform3f("lightDir",   -0.4f, -1.0f, -0.2f);
        shader.setUniform3f("lightColor",  1.0f,  0.95f, 0.8f);
        shader.setUniform3f("skyColor",    0.02f, 0.02f, 0.04f);
        shader.setUniform3f("viewPos",     camX,  5f,    camZ);
        shader.setUniform3f("rimColor",    0.3f,  0.5f,  1.0f);
        shader.setUniformFloat("rimStrength",     0.9f);
        shader.setUniformFloat("fogDensity",      0.0f);
        shader.setUniform3f("emissiveColor",      0f, 0f, 0f);
        shader.setUniformFloat("emissiveStrength", 0f);
        shader.setUniform3f("sunDir", 0.4f, 0.8f, 0.3f);
        
        for (Model m : models) m.render();
        shader.unbind();

        // Resto do menu 2D por cima (overlay escuro + textos)
        setup2DLegado();
        glDisable(GL_TEXTURE_2D);
        glColor4f(0f, 0f, 0f, 0.55f);
        glBegin(GL_QUADS);
        glVertex2f(0,0); glVertex2f(360,0);
        glVertex2f(360,720); glVertex2f(0,720);
        glEnd();

        glColor3f(0.6f, 0.05f, 0.05f);
        glLineWidth(2f);
        glBegin(GL_LINES);
        glVertex2f(360, 0); glVertex2f(360, 720);
        glEnd();
        glLineWidth(1f);

        glEnable(GL_TEXTURE_2D);
        glColor3f(1f, 1f, 1f);
        textRenderer.renderText("SR-71  BLACKBIRD", 40, 120);
        glColor3f(0.4f, 0.4f, 0.4f);
        textRenderer.renderText("────────────────────", 40, 145);

        String[] opcoes = { "JOGAR", "MISSOES", "OPCOES", "EXTRAS", "SAIR" };
        for (int i = 0; i < opcoes.length; i++) {
            if (i == menuSelectedIndex) {
                glColor3f(1f, 0.2f, 0.2f); // vermelho = selecionado
                textRenderer.renderText("> " + opcoes[i], 55, 210 + i * 60);
            } else {
                glColor3f(0.6f, 0.6f, 0.6f); // cinza = não selecionado
                textRenderer.renderText("  " + opcoes[i], 55, 210 + i * 60);
            }
        }

        // Atualiza o rodapé
        glColor3f(0.3f, 0.3f, 0.3f);
        textRenderer.renderText("Arrows = Navigate   ENTER = Confirm   ESC = Sair", 30, 680);
    }
    
    private void renderDebriefing() {
        setup2DLegado();
        glClearColor(0.02f, 0.04f, 0.02f, 1f);

        glDisable(GL_TEXTURE_2D);

        // Cabeçalho
        glColor3f(0f, 0.6f, 0f);
        glBegin(GL_QUADS);
        glVertex2f(80,60); glVertex2f(1200,60);
        glVertex2f(1200,90); glVertex2f(80,90);
        glEnd();

        glEnable(GL_TEXTURE_2D);
        glColor3f(0f, 0f, 0f);
        String result = stats.isCompleted() ? "MISSAO COMPLETA" : "MISSAO FALHOU";
        textRenderer.renderText(result, 500, 68);

        glColor3f(0f, 0.8f, 0f);
        textRenderer.renderText("DEBRIEFING CLASSIFICADO", 90, 68);

        // Linha separadora
        glDisable(GL_TEXTURE_2D);
        glColor3f(0f, 0.4f, 0f);
        glBegin(GL_LINES);
        glVertex2f(80, 110); glVertex2f(1200, 110);
        glEnd();

        glEnable(GL_TEXTURE_2D);

        // Dados da missão
        float lx = 150, rx = 700, y = 160;
        float step = 45;

        glColor3f(0f, 0.5f, 0f);
        textRenderer.renderText("MISSAO:", lx, y);
        glColor3f(0f, 1f, 0f);
        textRenderer.renderText(stats.getMissionName() != null ? stats.getMissionName() : "—", rx, y);
        y += step;
        glColor3f(0f, 0.5f, 0f);
        textRenderer.renderText("TEMPO DE VOO:", lx, y);
        glColor3f(0f, 1f, 0f);
        textRenderer.renderText(stats.getElapsedFormatted(), rx, y); y += step;

        glColor3f(0f, 0.5f, 0f);
            textRenderer.renderText("ALTITUDE MEDIA:", lx, y);
        glColor3f(0f, 1f, 0f);
        textRenderer.renderText(String.format("%.0f ft", stats.getAvgAltitude()), rx, y); y += step;

        glColor3f(0f, 0.5f, 0f);
        textRenderer.renderText("ALTITUDE MAXIMA:", lx, y);
        glColor3f(0f, 1f, 0f);
        textRenderer.renderText(String.format("%.0f ft", stats.getMaxAltitude()), rx, y); y += step;

        glColor3f(0f, 0.5f, 0f);
        textRenderer.renderText("VELOCIDADE MAXIMA:", lx, y);
        glColor3f(0f, 1f, 0f);
        textRenderer.renderText(String.format("%.0f km/h  (Mach %.2f)",
            stats.getMaxSpeed() * 3.6, stats.getMaxMach()), rx, y); y += step;

        glColor3f(0f, 0.5f, 0f);
        textRenderer.renderText("CONTRA-MEDIDAS USADAS:", lx, y);
        glColor3f(0f, 1f, 0f);
        textRenderer.renderText(String.valueOf(stats.getCountermeasures()), rx, y); y += step;

        // Classificação
        y += 20;
        glDisable(GL_TEXTURE_2D);
        glColor3f(0f, 0.3f, 0f);
        glBegin(GL_LINES);
        glVertex2f(80, y); glVertex2f(1200, y);
        glEnd();
        glEnable(GL_TEXTURE_2D);
        y += 25;

        String rating = getRating();
        glColor3f(0f, 1f, 0.3f);
        textRenderer.renderText("AVALIACAO:  " + rating, lx, y);

        glColor3f(0f, 0.4f, 0f);
        textRenderer.renderText("ENTER para continuar", 530, 680);
    }

    private String getRating() {
        if (!stats.isCompleted())        return "F  —  MISSAO FALHOU";
        if (stats.getMaxMach() >= 3.0)   return "S  —  CLASSIFICADO";
        if (stats.getMaxMach() >= 2.0)   return "A  —  EXCELENTE";
        if (stats.getElapsedSeconds() < 120) return "B  —  BOM";
        return "C  —  COMPLETO";
    }
    
    private void renderGameOver() {
        setup2DLegado();
        glClearColor(0f, 0f, 0f, 1f);
        glDisable(GL_TEXTURE_2D);

        // Linha vermelha no topo
        glColor3f(0.7f, 0f, 0f);
        glBegin(GL_QUADS);
        glVertex2f(0,0); glVertex2f(1280,0);
        glVertex2f(1280,6); glVertex2f(0,6);
        glEnd();

        glEnable(GL_TEXTURE_2D);
        glColor3f(0.8f, 0.05f, 0.05f);
        textRenderer.renderText("AERONAVE ABATIDA", 450, 280);
        glColor3f(0.5f, 0.5f, 0.5f);
        textRenderer.renderText("O SR-71 foi perdido.", 510, 330);
        textRenderer.renderText("ENTER para ver o debriefing", 460, 420);
    }
    
    private void confirmMenuSelection() {
        switch (menuSelectedIndex) {
	        case 0 -> {
	            stats.begin("Voo Livre");
	            startGameplay();
	            currentScreen = Screen.EXTERNAL;
	        }
            case 1 -> { // MISSOES
                currentScreen = Screen.MISSION_SELECT;
            }
            case 2 -> { // OPCOES
                // por enquanto não faz nada — placeholder
            }
            case 3 -> { // EXTRAS
                // placeholder
            }
            case 4 -> { // SAIR
                glfwSetWindowShouldClose(window, true);
            }
        }
    }
    
    private void confirmPauseSelection() {
        switch (pauseSelectedIndex) {
            case 0 -> { // Continuar
                paused = false;
            }
            case 1 -> { // Missões
                paused = false;
                stopGameplay();
                currentScreen = Screen.MISSION_SELECT;
            }
            case 2 -> { // Menu principal
                paused = false;
                stopGameplay();
                stats.setCompleted(false);
                currentScreen = Screen.MAIN_MENU;
            }
        }
    }
    
    private void renderPauseMenu() {
        // Renderiza a tela atual por baixo (congelada)
    	if (currentScreen == Screen.EXTERNAL) renderExternalScene();
    	else renderDataScreen();

        // Overlay escuro semi-transparente
        setup2DLegado();
        glDisable(GL_TEXTURE_2D);
        glColor4f(0f, 0f, 0f, 0.65f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        // Caixa central
        float bx = 490, by = 260, bw = 300, bh = 200;
        glColor4f(0.06f, 0.06f, 0.08f, 0.95f);
        glBegin(GL_QUADS);
        glVertex2f(bx, by); glVertex2f(bx + bw, by);
        glVertex2f(bx + bw, by + bh); glVertex2f(bx, by + bh);
        glEnd();

        // Borda vermelha
        glColor3f(0.6f, 0.05f, 0.05f);
        glLineWidth(2f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(bx, by); glVertex2f(bx + bw, by);
        glVertex2f(bx + bw, by + bh); glVertex2f(bx, by + bh);
        glEnd();
        glLineWidth(1f);

        glEnable(GL_TEXTURE_2D);
        glColor3f(0.8f, 0.8f, 0.8f);
        textRenderer.renderText("PAUSADO", bx + 105, by + 25);

        glColor3f(0.3f, 0.3f, 0.3f);
        glDisable(GL_TEXTURE_2D);
        glBegin(GL_LINES);
        glVertex2f(bx + 20, by + 50); glVertex2f(bx + bw - 20, by + 50);
        glEnd();
        glEnable(GL_TEXTURE_2D);

        String[] opcoes = { "CONTINUAR", "MISSOES", "MENU PRINCIPAL" };
        for (int i = 0; i < opcoes.length; i++) {
            float oy = by + 75 + i * 40;
            if (i == pauseSelectedIndex) {
                glColor3f(1f, 0.25f, 0.25f);
                textRenderer.renderText("> " + opcoes[i], bx + 40, oy);
            } else {
                glColor3f(0.55f, 0.55f, 0.55f);
                textRenderer.renderText("  " + opcoes[i], bx + 40, oy);
            }
        }

        glColor3f(0.3f, 0.3f, 0.3f);
        textRenderer.renderText("ESC = Continuar", bx + 70, by + bh - 18);
    }

    private void renderWithPostProcess(float delta) {
        postTime += delta;

        // ── PASSO 1: Renderiza cena no FBO ──────────────────────────
        sceneBuffer.bind();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        renderExternalScene(); // veja abaixo
        sceneBuffer.unbind();

        // ── PASSO 2: Heat haze (distorção nas turbinas) ───────────────
        blurBufferH.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);

        // Projeta posição das turbinas para UV de tela
        FlyByWire.FlightData data = fbw.getLastData();
        Vector3f posReal  = fbw.getPosicao();
        Vector3f planePos = new Vector3f(posReal.x, (float)data.altitude / 10f, posReal.z);

        // Offset aproximado das turbinas (atrás e abaixo do avião)
        Vector3f turbineWorld = new Vector3f(planePos).add(0, -0.5f, 1.5f);
        Vector3f screenUV     = worldToScreenUV(turbineWorld);

        heatHazeShader.bind();
        heatHazeShader.setUniform1i("scene", 0);
        heatHazeShader.setUniformFloat("time", postTime);
        heatHazeShader.setUniform2f("turbineScreenPos", screenUV.x, screenUV.y);
        heatHazeShader.setUniformFloat("hazeStrength", 0.003f);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneBuffer.getTexture());
        postQuad.render();
        heatHazeShader.unbind();
        blurBufferH.unbind();

        // ── PASSO 3: Extrai brilho ───────────────────────────────────
        brightBuffer.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        bloomBrightShader.bind();
        bloomBrightShader.setUniform1i("scene", 0);
        bloomBrightShader.setUniformFloat("threshold", 0.88f);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, blurBufferH.getTexture());
        postQuad.render();
        bloomBrightShader.unbind();
        brightBuffer.unbind();

        // ── PASSO 4: Blur horizontal ─────────────────────────────────
        blurBufferV.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        bloomBlurShader.bind();
        bloomBlurShader.setUniform1i("image", 0);
        bloomBlurShader.setUniform1i("horizontal", 1);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, brightBuffer.getTexture());
        postQuad.render();
        bloomBlurShader.unbind();
        blurBufferV.unbind();

        // ── PASSO 5: Blur vertical ───────────────────────────────────
        brightBuffer.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        bloomBlurShader.bind();
        bloomBlurShader.setUniform1i("image", 0);
        bloomBlurShader.setUniform1i("horizontal", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, blurBufferV.getTexture());
        postQuad.render();
        bloomBlurShader.unbind();
        brightBuffer.unbind();

        // ── PASSO 6: Composite final ─────────────────────────────────
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        finalPostShader.bind();
        finalPostShader.setUniform1i("scene",     0);
        finalPostShader.setUniform1i("bloomBlur", 1);
        finalPostShader.setUniformFloat("bloomStrength", 0.04f);
        finalPostShader.setUniformFloat("exposure", 1.25f); 
        finalPostShader.setUniformFloat("vignetteStr",   0.25f);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, blurBufferH.getTexture()); // cena com haze
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, brightBuffer.getTexture()); // bloom
        postQuad.render();
        finalPostShader.unbind();

        // ── HUD por cima (sem post-processing) ───────────────────────
        glEnable(GL_DEPTH_TEST);
        
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);

        glEnable(GL_DEPTH_TEST);
        renderHUD();
        renderCountermeasureHUD();
    }
    
    private Vector3f worldToScreenUV(Vector3f worldPos) {
        org.joml.Vector4f clip = new org.joml.Vector4f(worldPos, 1.0f);
        clip = camera.getProjectionMatrix().mul(camera.getViewMatrix(),
               new org.joml.Matrix4f()).transform(clip);
        if (clip.w == 0) return new Vector3f(0.5f, 0.5f, 0);
        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        return new Vector3f((ndcX + 1f) / 2f, (ndcY + 1f) / 2f, 0);
    }
    
    public static void main(String[] args) { new FlyData().run(); }
}