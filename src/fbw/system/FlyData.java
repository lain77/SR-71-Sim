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
import fbw.assets.EngineAudio;
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
    private SkyRenderer skyRenderer;
    private int waterVaoId;

    private boolean borderlessFullscreen = false;
    
    private HeightmapTerrain heightmapTerrain;
    private int satelliteTextureId;
    
    private float missionBarY       = 110;
    private float missionBarTargetY = 110;
    
    private int texPilots, texMD11, texTakeoff, texSR71One;
    private int texWeaver, texGilliland, texMarta, texCrews, texPilot23;
    
 // Transição VHS
    private boolean  transitioning = false;
    private float    transTimer    = 0f;
    private float    transDuration = 0.8f;  // duração total em segundos
    private Screen   transTarget   = null;
    private boolean  transHalfDone = false;
    
    private int screenWidth = 1280;
    private int screenHeight = 720;
    
    private float crtTime = 0f;
    private int     rsoTextureId;
    private boolean rsoVisible    = false;
    private float   rsoTimer      = 0f;
    private String  rsoMessage    = "";
    private static final float RSO_DURATION = 4f;
    
 // Menu animation
    private float menuBarY       = 385;
    private float menuBarTargetY = 385;
    private float menuBarAlpha   = 0f;    // fade in da barra
    
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
    
    private float glitchTimer    = 0f;
    private float glitchDuration = 0f;
    private float glitchOffsetX  = 0f;
    private boolean hudFlicker   = false;
    
    private int currentLivery = 0;
    private String[] liveryPaths = {
        "src/models/sr71/sr71nochute.glb",   // 0: Blackbird (padrão)
        "src/models/sr71/sr71white.glb",     // 1: WhiteBird (rara)
        // futuras:
        // "src/models/sr71/sr71nasa.glb",   // 2: NASA YF-12
        // "src/models/sr71/sr71camo.glb",   // 3: Prototype Camo
    };
    private String[] liveryNames = {
        "SR-71 BLACKBIRD",
        "SR-71 WHITEBIRD [RARE]",
    };
    private boolean[] liveryUnlocked = {
        true,    // Blackbird sempre desbloqueado
        false,   // WhiteBird — desbloqueia com Mach 3.5+
    };
    
    private Camera camera;
    private List<Model> models = new ArrayList<>(); 

    private boolean introMusicStarted = false;
    private int logoTextureId;
    
    private List<Model> terrainModels = new ArrayList<>();
    private AIScene     terrainScene;
    
    private long window;
    private int mapTextureId;
    private int backgroundTextureId;

    private boolean paused = false;
    private boolean cameraFixedMode = false;
    
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

    private int     cameraFlashTimer  = 0;
    private boolean photoTaken        = false;
    private float   photoPopupTimer   = 0f;
    private int     capturedPhotoTex  = -1;
    
    private int sEsquerdaX = 50, sEsquerdaY = 50;
    private int sDireitaX = 1000, sDireitaY = 50;
    private int sCentroX = 680, sCentroY = 50;

    private FlightStats stats        = new FlightStats();
    private float       introTimer   = 0f;
    private float       logoTimer    = 0f;
    private boolean     introSkipped = false;
    private float       deathTimer   = 0f;
    private float lastDelta = 0.016f;
    private EngineAudio engineAudio;
    
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
        CAMERA,
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
        glfwSwapInterval(0);
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

        fbw.setPosicao(new Vector3f(0, 60000f, 0)); 

        logoTextureId = loadTexture("src/img/logoox.jpg");
        
        rsoTextureId = loadTexture("src/img/brianshul.jpg");
        
        texPilots  = loadTexture("src/img/sr71pilots.jpg");
        texMD11    = loadTexture("src/img/md11.jpg");
        texTakeoff = loadTexture("src/img/sr71takeoff.jpg");
        texSR71One = loadTexture("src/img/sr71one.jpg");
        texWeaver    = loadTexture("src/img/bill_weaver.jpg");
        texGilliland = loadTexture("src/img/SR-71-Gilliland.jpg");
        texMarta     = loadTexture("src/img/SR-71-Marta-Bohn-Meyer.jpg");
        texCrews     = loadTexture("src/img/SR-71-crews.jpg");
        texPilot23   = loadTexture("src/img/pilot23.jpg");
        
        mapTextureId = loadTexture("src/img/EuropeMap.PNG");
        backgroundTextureId = loadBackgroundImage();

        groundTextureId = loadTexture("src/img/grass.jpg");
        
        rockTextureId = loadTexture("src/img/rock.jpg");
        snowTextureId = loadTexture("src/img/snow.jpg");
        
        heightmapTerrain = new HeightmapTerrain(
        	    "src/img/terrain_heightmap.png",
        	    1600000f,    // largura em ft (~490 km)
        	    800000f,     // profundidade em ft (~245 km)
        	    18000f,      // altura máxima (Monte Elbrus ~18,000 ft)
        	    8            // pixels com valor <= 8 = mar
        	);
        	satelliteTextureId = loadTexture("src/img/terrain_satellite.png");
        waterVaoId = createWaterPlane();
        
        init3DSystem();
    }

    private void startGameplay() {
        if (gameplayStarted) return;
        System.out.println("START POS: " + fbw.getPosicao());
        fbw.start();
        enemy.start();
        audio.stopMusic();
        engineAudio.start(); 
        gameplayStarted = true;
    }

    private void stopGameplay() {
        if (!gameplayStarted) return;
        enemy.stop();
        fbw.stop();
        fbw.revive();
        fbw.setPosicao(new Vector3f(0, 60000f, 0));
        fbw.setThrottle(300); 
        engineAudio.stop();
        gameplayStarted = false;
        previousScreen = Screen.MAIN_MENU;
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
            skyRenderer = new SkyRenderer();
            
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
            engineAudio = new EngineAudio();
            engineAudio.init("src/audio/engine.wav");

            scene = Assimp.aiImportFile("src/models/sr71/sr71white.glb",
                    Assimp.aiProcess_Triangulate | Assimp.aiProcess_FlipUVs | Assimp.aiProcess_GenNormals);

//            terrainScene = Assimp.aiImportFile(
//            	    "src/models/assets/snowy_mountain_-_terrain.glb",
//            	    Assimp.aiProcess_Triangulate |
//            	    Assimp.aiProcess_FlipUVs     |
//            	    Assimp.aiProcess_GenNormals
//            	);
//
//            	if (terrainScene == null || terrainScene.mRootNode() == null) {
//            	    System.out.println(" Terreno GLB não carregado: " + Assimp.aiGetErrorString());
//            	} else {
//            	    terrainModels.addAll(Model.loadAllFromScene(terrainScene));
//            	    System.out.println(" Terreno carregado: " + terrainModels.size() + " meshes");
//            	}
            
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
                    else if (currentScreen == Screen.LOGO)           currentScreen = Screen.INTRO;
                    else if (currentScreen == Screen.INTRO)          { introSkipped = true; currentScreen = Screen.MAIN_MENU; }
                    else if (currentScreen == Screen.MAIN_MENU)      confirmMenuSelection();
                    else if (currentScreen == Screen.GAME_OVER)      { fbw.revive(); transitionTo(Screen.DEBRIEFING); }
                    else if (currentScreen == Screen.DEBRIEFING)     { stopGameplay(); transitionTo(Screen.MISSION_SELECT); }
                    else if (currentScreen == Screen.MISSION_SELECT)  selectAndStart(missionSelectedIndex);
                }
            }
            
            case GLFW_KEY_ESCAPE -> {
                if (pressed) {
                    if (currentScreen == Screen.EXTERNAL || currentScreen == Screen.DATA) {
                        paused = !paused;
                    } else if (currentScreen == Screen.MISSION_SELECT) {
                        currentScreen = Screen.MAIN_MENU;
                    } else {
                        escStartPressed = true;
                    }
                }
            }
            case GLFW_KEY_UP -> {
                if (pressed) {
                    if (paused) { pauseSelectedIndex = Math.max(0, pauseSelectedIndex - 1); break; }
                    if (currentScreen == Screen.MAIN_MENU) {
                        menuSelectedIndex = Math.max(0, menuSelectedIndex - 1);
                        menuBarTargetY = 385 + menuSelectedIndex * 50;
                    	}
                    else if (currentScreen == Screen.MISSION_SELECT) {
                        missionSelectedIndex = Math.max(0, missionSelectedIndex - 1);
                        missionBarTargetY = 140 + missionSelectedIndex * 100;
                    }
                    else if (currentScreen == Screen.EXTERNAL || currentScreen == Screen.DATA)
                        fbw.setPitchInput(1);
                } else {
                    // Soltou a tecla — para de rotacionar
                    if (currentScreen == Screen.EXTERNAL || currentScreen == Screen.DATA)
                        fbw.setPitchInput(0);
                }
            }
            case GLFW_KEY_DOWN -> {
                if (pressed) {
                    if (paused) { pauseSelectedIndex = Math.min(2, pauseSelectedIndex + 1); break; }
                    if (currentScreen == Screen.MAIN_MENU) {
                        menuSelectedIndex = Math.min(4, menuSelectedIndex + 1);
                        menuBarTargetY = 385 + menuSelectedIndex * 50; }
                    else if (currentScreen == Screen.MISSION_SELECT) {
                        missionSelectedIndex = Math.min(missionManager.getMissions().size() - 1, missionSelectedIndex + 1);
                        missionBarTargetY = 140 + missionSelectedIndex * 100;
                    }
                    else if (currentScreen == Screen.EXTERNAL || currentScreen == Screen.DATA)
                        fbw.setPitchInput(-1);
                } else {
                    if (currentScreen == Screen.EXTERNAL || currentScreen == Screen.DATA)
                        fbw.setPitchInput(0);
                }
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
                        // Só permite mapa durante o voo
                        boolean emVoo = currentScreen == Screen.EXTERNAL 
                                     || currentScreen == Screen.DATA;
                        boolean noMapa = currentScreen == Screen.MAP;
                        
                        if (emVoo) {
                            previousScreen = currentScreen;
                            currentScreen = Screen.MAP;
                        } else if (noMapa) {
                            currentScreen = previousScreen;
                        }
                        // Ignora M em qualquer outra tela
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
                        boolean emVoo = currentScreen == Screen.EXTERNAL 
                                     || currentScreen == Screen.DATA;
                        if (emVoo) {
                            previousScreen = currentScreen;
                            missionManager.openMenu();
                            currentScreen = Screen.MISSION_SELECT;
                        } else if (currentScreen == Screen.MISSION_SELECT) {
                            currentScreen = previousScreen;
                        }
                    }
                }
                case GLFW_KEY_F -> {
                    if (pressed) {
                        if (currentScreen == Screen.EXTERNAL) {
                            currentScreen = Screen.CAMERA;
                        } else if (currentScreen == Screen.CAMERA) {
                            currentScreen = Screen.EXTERNAL;
                        }
                    }
                }
                case GLFW_KEY_W -> {
                    if (pressed || action == GLFW_REPEAT)
                        fbw.throttleUp(1);
                }
                case GLFW_KEY_Q -> {
                    if (pressed || action == GLFW_REPEAT)
                        fbw.throttleDown(1);
                }
                case GLFW_KEY_L -> {
                    if (pressed) {
                        int next = (currentLivery + 1) % liveryPaths.length;
                        for (int i = 0; i < liveryPaths.length; i++) {
                            if (liveryUnlocked[next]) break;
                            next = (next + 1) % liveryPaths.length;
                        }
                        loadLivery(next);
                        triggerRSO("LIVERY: " + liveryNames[next]);
                    }
                }
                case GLFW_KEY_F11 -> {
                    if (pressed) {
                        long monitor = glfwGetPrimaryMonitor();
                        GLFWVidMode mode = glfwGetVideoMode(monitor);
                        if (!borderlessFullscreen) {
                            glfwSetWindowAttrib(window, GLFW_DECORATED, GLFW_FALSE);
                            glfwSetWindowPos(window, 0, 0);
                            glfwSetWindowSize(window, mode.width(), mode.height());
                            borderlessFullscreen = true;
                        } else {
                            glfwSetWindowAttrib(window, GLFW_DECORATED, GLFW_TRUE);
                            glfwSetWindowPos(window, 100, 100);
                            glfwSetWindowSize(window, 1280, 720);
                            borderlessFullscreen = false;
                        }
                        int[] w = new int[1], h = new int[1];
                        glfwGetFramebufferSize(window, w, h);
                        screenWidth = w[0];
                        screenHeight = h[0];
                        glViewport(0, 0, screenWidth, screenHeight);
                        camera.updateAspect(screenWidth, screenHeight);
                        textRenderer.setScale(screenWidth / 1280f);

                        if (sceneBuffer  != null) sceneBuffer.cleanup();
                        if (brightBuffer != null) brightBuffer.cleanup();
                        if (blurBufferH  != null) blurBufferH.cleanup();
                        if (blurBufferV  != null) blurBufferV.cleanup();
                        sceneBuffer  = new Framebuffer(screenWidth, screenHeight);
                        brightBuffer = new Framebuffer(screenWidth, screenHeight);
                        blurBufferH  = new Framebuffer(screenWidth, screenHeight);
                        blurBufferV  = new Framebuffer(screenWidth, screenHeight);
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
        
        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                rightMouseDown = (action == GLFW_PRESS);
                if (!rightMouseDown) { lastMouseX = -1; lastMouseY = -1; }
            }
            // NOVO: clique esquerdo na câmera fotografa
            if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                if (currentScreen == Screen.CAMERA) {
                    attemptPhoto();
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
        glUseProgram(0); 
        glBindVertexArray(0);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1); 
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glScalef(screenWidth / 1280f, screenHeight / 720f, 1f);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }

    private void loop() {
        lastFrameTime = System.currentTimeMillis();
    	
        while (!glfwWindowShouldClose(window)) {
            // Delta time em segundos
            long now   = System.currentTimeMillis();
            float delta = (now - lastFrameTime) / 1000f;
            lastDelta   = Math.min(delta, 0.05f);
            boolean emVoo = currentScreen == Screen.EXTERNAL 
                    || currentScreen == Screen.DATA
                    || currentScreen == Screen.CAMERA;

            // Checa morte
            if (emVoo && !paused) {
                stats.sample(fbw.getLastData());
                checkTerrainCollision();

                if (fbw.isDead()) {
                    deathTimer += delta;
                    if (deathTimer >= 2.5f) {
                        stats.setCompleted(false);
                        stopGameplay();
                        transitionTo(Screen.GAME_OVER);
                        deathTimer = 0f;
                    }
                }

                missionManager.update(delta, fbw, enemy);
                
                weather.update(delta, fbw);
                
                FlyByWire.FlightData ed = fbw.getLastData();
                if (ed != null) {
                    double fuelPct = (fbw.getFuel() / 100000.0) * 100.0;
                    engineAudio.update(fbw.getThrottle(), ed.mach, ed.altitude, fuelPct);
                }

                // Mostra RSO quando EngineAudio trigga voice line
                if (engineAudio.pendingRSOMessage != null) {
                    triggerRSO(engineAudio.pendingRSOMessage);
                    engineAudio.pendingRSOMessage = null;
                }
                
                if (enemy.isMissileWarning()) {
                    engineAudio.playSAMWarning(); 
                }
                
                // Checa missão completa
                Mission current = missionManager.currentMission();
                
                if (current != null) {
                    String rsoMsg = current.checkRSO();
                    if (rsoMsg != null) {
                        triggerRSO(rsoMsg);
                    }
                }
                
                if (current != null && current.getState() == Mission.State.SUCCESS) {
                    stats.setCompleted(true);
                    stopGameplay();                     
                    transitionTo(Screen.DEBRIEFING); 
                }
            }
            
            lastFrameTime = now;
            crtTime += lastDelta;
            updateHudGlitch();

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
                case CAMERA 		-> renderCameraScreen();
                case MISSION_SELECT -> renderMissionSelect();
                case DEBRIEFING     -> renderDebriefing();
                case GAME_OVER      -> renderGameOver();
            }
           }

            if (transitioning) {
                transTimer += lastDelta;
                float progress = transTimer / transDuration;

                // Na metade, troca a tela de verdade
                if (progress >= 0.5f && !transHalfDone) {
                    currentScreen = transTarget;
                    transHalfDone = true;
                }

                // Renderiza efeito VHS
                renderVHSTransition(progress);

                if (progress >= 1.0f) {
                    transitioning = false;
                    transTarget   = null;
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
        glClearColor(0f, 0f, 0f, 1f);

        float t = crtTime;
        missionBarY += (missionBarTargetY - missionBarY) * 0.12f;

        var missions = missionManager.getMissions();
        var sel = missions.get(missionSelectedIndex);

        // ══════════════════════════════════════════════════════════
        // IMAGEM DE FUNDO — mesma lógica do menu principal
        // ══════════════════════════════════════════════════════════
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int[] bgTextures = {
            texPilots, texTakeoff, texSR71One, texMD11,
            texWeaver, texGilliland, texMarta, texCrews,
            texPilot23, rsoTextureId
        };

        float cycleDuration = 5f;
        float fadeDuration  = 1.5f;
        float blackGap      = 0.5f;
        float totalCycle    = cycleDuration + blackGap;

        int currentImg = ((int)(t / totalCycle)) % bgTextures.length;
        float cycleProgress = (t % totalCycle);

        float imgAlpha = 0f;
        if (cycleProgress < fadeDuration)
            imgAlpha = cycleProgress / fadeDuration;
        else if (cycleProgress < cycleDuration - fadeDuration)
            imgAlpha = 1f;
        else if (cycleProgress < cycleDuration)
            imgAlpha = (cycleDuration - cycleProgress) / fadeDuration;
        imgAlpha *= 0.2f;

        float zoom = 1.05f + (cycleProgress / cycleDuration) * 0.04f;
        float panX = (float)(Math.sin(t * 0.06 + currentImg * 1.5) * 35);
        float panY = (float)(Math.cos(t * 0.04 + currentImg * 2.1) * 20);

        if (imgAlpha > 0.001f) {
            float imgW = 1280 * zoom;
            float imgH = 720 * zoom;
            float ix = (1280 - imgW) / 2 + panX;
            float iy = (720 - imgH) / 2 + panY;

            glColor4f(0.7f, 0.72f, 0.85f, imgAlpha);
            glBindTexture(GL_TEXTURE_2D, bgTextures[currentImg]);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(ix, iy);
            glTexCoord2f(1, 0); glVertex2f(ix + imgW, iy);
            glTexCoord2f(1, 1); glVertex2f(ix + imgW, iy + imgH);
            glTexCoord2f(0, 1); glVertex2f(ix, iy + imgH);
            glEnd();
            glBindTexture(GL_TEXTURE_2D, 0);
        }

        // ══════════════════════════════════════════════════════════
        // GRADIENTES
        // ══════════════════════════════════════════════════════════
        glDisable(GL_TEXTURE_2D);

        // Topo
        glBegin(GL_QUADS);
        glColor4f(0f, 0f, 0f, 0.8f);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glColor4f(0f, 0f, 0f, 0.15f);
        glVertex2f(1280, 120); glVertex2f(0, 120);
        glEnd();

        // Bottom
        glBegin(GL_QUADS);
        glColor4f(0f, 0f, 0f, 0.15f);
        glVertex2f(0, 600); glVertex2f(1280, 600);
        glColor4f(0f, 0f, 0f, 0.8f);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        // Esquerda (protege lista de missões)
        glBegin(GL_QUADS);
        glColor4f(0f, 0f, 0f, 0.85f);
        glVertex2f(0, 0); glVertex2f(0, 720);
        glColor4f(0f, 0f, 0f, 0.3f);
        glVertex2f(440, 720); glVertex2f(440, 0);
        glEnd();

        // Direita (protege briefing)
        glBegin(GL_QUADS);
        glColor4f(0f, 0f, 0f, 0.1f);
        glVertex2f(440, 0); glVertex2f(440, 720);
        glColor4f(0f, 0f, 0f, 0.6f);
        glVertex2f(1280, 720); glVertex2f(1280, 0);
        glEnd();

        // ══════════════════════════════════════════════════════════
        // PARTÍCULAS
        // ══════════════════════════════════════════════════════════
        glPointSize(1.5f);
        glBegin(GL_POINTS);
        for (int i = 0; i < 35; i++) {
            float sx = (float)(Math.sin(i * 127.1) * 0.5 + 0.5) * 1280;
            float sy = ((float)(Math.cos(i * 311.7) * 0.5 + 0.5) * 720 - t * (2 + i % 6));
            sy = ((sy % 780) + 780) % 780 - 30;
            float sa = (float)(Math.sin(t * 0.8 + i * 0.5) * 0.5 + 0.5) * 0.15f;
            glColor4f(0.7f, 0.72f, 0.9f, sa);
            glVertex2f(sx, sy);
        }
        glEnd();
        glPointSize(1f);

        // ══════════════════════════════════════════════════════════
        // LINHA DIVISÓRIA VERTICAL — separa lista de briefing
        // ══════════════════════════════════════════════════════════
        float divAlpha = 0.2f + (float)(Math.sin(t * 1.5) * 0.05);
        glColor4f(0.6f, 0.62f, 0.75f, divAlpha);
        glBegin(GL_QUADS);
        glVertex2f(430, 80); glVertex2f(431, 80);
        glVertex2f(431, 660); glVertex2f(430, 660);
        glEnd();

        // ══════════════════════════════════════════════════════════
        // CABEÇALHO
        // ══════════════════════════════════════════════════════════
        glEnable(GL_TEXTURE_2D);

        // Título
        glColor4f(1f, 1f, 1f, 1f);
        textRenderer.renderText("MISSION SELECT", 40, 50, 48);

        // Linha sob o título
        glDisable(GL_TEXTURE_2D);
        float headerLineW = Math.min(380, t * 200);
        glColor4f(0.6f, 0.62f, 0.75f, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(40, 78); glVertex2f(40 + headerLineW, 78);
        glVertex2f(40 + headerLineW, 79); glVertex2f(40, 79);
        glEnd();

        // Subtítulo
        glEnable(GL_TEXTURE_2D);
        glColor4f(0.45f, 0.47f, 0.55f, 0.7f);
        textRenderer.renderText("CLASSIFIED OPERATIONS // SR-71 PROGRAM", 40, 92, 16);

        // ══════════════════════════════════════════════════════════
        // BARRA DE SELEÇÃO — desliza entre missões
        // ══════════════════════════════════════════════════════════
        glDisable(GL_TEXTURE_2D);

        float mBarTop = missionBarY - 8;
        float mBarBot = missionBarY + 82;

        // Glow
        glColor4f(0.4f, 0.42f, 0.6f, 0.1f);
        glBegin(GL_QUADS);
        glVertex2f(0, mBarTop - 6);
        glVertex2f(425, mBarTop - 6);
        glVertex2f(425, mBarBot + 6);
        glVertex2f(0, mBarBot + 6);
        glEnd();

        // Barra vertical fina
        glColor4f(0.85f, 0.88f, 1f, 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(28, mBarTop + 4);
        glVertex2f(31, mBarTop + 4);
        glVertex2f(31, mBarBot - 4);
        glVertex2f(28, mBarBot - 4);
        glEnd();

        // ══════════════════════════════════════════════════════════
        // LISTA DE MISSÕES (esquerda)
        // ══════════════════════════════════════════════════════════
        glEnable(GL_TEXTURE_2D);

        for (int i = 0; i < missions.size(); i++) {
            var m = missions.get(i);
            float baseY = 140 + i * 100;
            boolean isSel = (i == missionSelectedIndex);
            float proximity = 1f - Math.min(1f, Math.abs(missionBarY - baseY) / 100f);

            // Número da missão
            if (isSel) {
                glColor4f(0.85f, 0.88f, 1f, 0.8f);
            } else {
                glColor4f(0.25f, 0.25f, 0.3f, 0.5f + proximity * 0.2f);
            }
            textRenderer.renderText(String.format("%02d", i + 1), 40, baseY, 32);

            // Nome da missão
            if (isSel) {
                glColor4f(1f, 1f, 1f, 1f);
            } else {
                float b = 0.4f + proximity * 0.2f;
                glColor4f(b, b, b + 0.05f, 0.85f);
            }
            textRenderer.renderText(m.getName(), 85, baseY, 24);

            // Tipo da missão
            String tipo = m.getClass().getSimpleName()
                .replace("Mission", "")
                .replace("Recon", "RECONNAISSANCE")
                .replace("Escape", "EVASION")
                .replace("FlightWindow", "FLIGHT ENVELOPE");

            if (isSel)
                glColor4f(0.55f, 0.58f, 0.7f, 0.8f);
            else
                glColor4f(0.3f, 0.3f, 0.35f, 0.5f + proximity * 0.15f);
            textRenderer.renderText(tipo, 85, baseY + 25, 16);

            // Status
            String statusTag = switch (m.getState()) {
                case WAITING -> "";
                case ACTIVE  -> "[ACTIVE]";
                case SUCCESS -> "[COMPLETE]";
                case FAILED  -> "[FAILED]";
            };
            if (!statusTag.isEmpty()) {
                float[] sc = switch (m.getState()) {
                    case SUCCESS -> new float[]{0.3f, 0.9f, 0.4f, 0.7f};
                    case FAILED  -> new float[]{0.9f, 0.3f, 0.3f, 0.7f};
                    case ACTIVE  -> new float[]{0.9f, 0.9f, 0.3f, 0.7f};
                    default      -> new float[]{0.5f, 0.5f, 0.5f, 0.5f};
                };
                glColor4f(sc[0], sc[1], sc[2], sc[3]);
                textRenderer.renderText(statusTag, 85, baseY + 45, 16);
            }
        }

        // ══════════════════════════════════════════════════════════
        // BRIEFING DA MISSÃO SELECIONADA (direita)
        // ══════════════════════════════════════════════════════════

        // Nome grande da missão
        glColor4f(1f, 1f, 1f, 0.95f);
        textRenderer.renderText(sel.getName(), 460, 130, 32);

        // Linha sob o nome
        glDisable(GL_TEXTURE_2D);
        glColor4f(0.5f, 0.52f, 0.65f, 0.4f);
        glBegin(GL_QUADS);
        glVertex2f(460, 158); glVertex2f(1200, 158);
        glVertex2f(1200, 159); glVertex2f(460, 159);
        glEnd();

        // Label BRIEFING
        glEnable(GL_TEXTURE_2D);
        glColor4f(0.5f, 0.55f, 0.7f, 0.7f);
        textRenderer.renderText("BRIEFING", 460, 175, 16);

        // Texto do briefing
        glColor4f(0.7f, 0.72f, 0.8f, 0.85f);
        String[] lines = sel.getBriefing().split("\n");
        for (int l = 0; l < lines.length; l++) {
            // Linhas vazias = headers, ficam mais claras
            if (lines[l].trim().isEmpty()) continue;
            if (lines[l].equals(lines[l].toUpperCase()) && lines[l].length() < 40) {
                glColor4f(0.85f, 0.87f, 0.95f, 0.9f);
                textRenderer.renderText(lines[l], 460, 200 + l * 22, 20);
            } else {
                glColor4f(0.6f, 0.62f, 0.72f, 0.8f);
                textRenderer.renderText(lines[l], 460, 200 + l * 22, 16);
            }
        }

        // ══════════════════════════════════════════════════════════
        // STATUS DA MISSÃO
        // ══════════════════════════════════════════════════════════

        // Linha separadora
        glDisable(GL_TEXTURE_2D);
        glColor4f(0.4f, 0.42f, 0.55f, 0.3f);
        glBegin(GL_QUADS);
        glVertex2f(460, 520); glVertex2f(1200, 520);
        glVertex2f(1200, 521); glVertex2f(460, 521);
        glEnd();

        glEnable(GL_TEXTURE_2D);
        glColor4f(0.5f, 0.55f, 0.7f, 0.6f);
        textRenderer.renderText("STATUS", 460, 535, 16);

        String statusText = switch (sel.getState()) {
            case WAITING -> "READY FOR DEPLOYMENT";
            case ACTIVE  -> "IN PROGRESS";
            case SUCCESS -> "MISSION ACCOMPLISHED";
            case FAILED  -> "MISSION FAILED — RETRY AVAILABLE";
        };
        float[] stColor = switch (sel.getState()) {
            case WAITING -> new float[]{0.6f, 0.62f, 0.7f};
            case ACTIVE  -> new float[]{0.9f, 0.9f, 0.4f};
            case SUCCESS -> new float[]{0.3f, 0.95f, 0.45f};
            case FAILED  -> new float[]{0.95f, 0.3f, 0.3f};
        };
        glColor3f(stColor[0], stColor[1], stColor[2]);
        textRenderer.renderText(statusText, 460, 558, 20);

        // ══════════════════════════════════════════════════════════
        // CONTROLES
        // ══════════════════════════════════════════════════════════
        glColor4f(0.35f, 0.35f, 0.4f, 0.5f);
        textRenderer.renderText("NAVIGATE", 460, 640, 16);
        textRenderer.renderText("START MISSION", 460, 658, 16);
        textRenderer.renderText("BACK", 460, 676, 16);
        glColor4f(0.6f, 0.6f, 0.65f, 0.6f);
        textRenderer.renderText("UP / DOWN", 590, 640, 16);
        textRenderer.renderText("ENTER", 590, 658, 16);
        textRenderer.renderText("ESC", 590, 676, 16);

        // ══════════════════════════════════════════════════════════
        // BARRAS FINAS
        // ══════════════════════════════════════════════════════════
        glDisable(GL_TEXTURE_2D);
        glColor4f(0.6f, 0.62f, 0.75f, 0.25f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glVertex2f(1280, 1); glVertex2f(0, 1);
        glEnd();
        glBegin(GL_QUADS);
        glVertex2f(0, 719); glVertex2f(1280, 719);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        drawCRTOverlay();
    }
    
    private void renderDataScreen() {
        glClearColor(0.03f, 0.03f, 0.05f, 1f);
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        setup2DLegado();
        glEnable(GL_TEXTURE_2D);

        // Centro: instrumentos principais
        drawArtificialHorizon(500, 300, data);
        drawCompassRose(500, 570, data);
        drawVSI(620, 500, data);

        // Esquerda baixo: gráfico + barras
        drawSpeedGraph(30, 350, data);
        drawSystemsPanel(data);

        // Direita: missão + radar
        drawMissionInfo(830, 230, data);
        drawRadar(1120, 570, 100);

        // HUD verde por cima (painéis topo-esquerda e topo-direita)
        renderHUD();
        renderCountermeasureHUD();
        drawGForce();
        drawCRTOverlay();
    }

    // ── HORIZONTE ARTIFICIAL ──────────────────────────────────────────
    private void drawArtificialHorizon(float cx, float cy, FlyByWire.FlightData data) {
        setup2DLegado();
        glDisable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);

        float size = 120f;
        float pitch = (float) data.pitch;
        float roll  = (float) data.roll;
        float pitchOffset = pitch * 2.5f;

        // ── Fundo circular com clip ──────────────────────────────
        // Céu — gradiente azul
        glBegin(GL_QUADS);
        glColor3f(0.08f, 0.2f, 0.5f);
        glVertex2f(cx - size, cy - size);
        glVertex2f(cx + size, cy - size);
        glColor3f(0.15f, 0.35f, 0.7f);
        glVertex2f(cx + size, cy + pitchOffset);
        glVertex2f(cx - size, cy + pitchOffset);
        glEnd();

        // Terra — gradiente marrom
        glBegin(GL_QUADS);
        glColor3f(0.35f, 0.22f, 0.08f);
        glVertex2f(cx - size, cy + pitchOffset);
        glVertex2f(cx + size, cy + pitchOffset);
        glColor3f(0.2f, 0.12f, 0.04f);
        glVertex2f(cx + size, cy + size);
        glVertex2f(cx - size, cy + size);
        glEnd();

        // Linha do horizonte
        glColor3f(1f, 1f, 1f);
        glLineWidth(2f);
        glBegin(GL_LINES);
        glVertex2f(cx - size, cy + pitchOffset);
        glVertex2f(cx + size, cy + pitchOffset);
        glEnd();

        // ── Marcas de pitch (cada 10 graus) ──────────────────────
        glColor4f(1f, 1f, 1f, 0.5f);
        glLineWidth(1f);
        for (int deg = -30; deg <= 30; deg += 10) {
            if (deg == 0) continue;
            float markY = cy + pitchOffset - deg * 2.5f;
            if (markY < cy - size + 10 || markY > cy + size - 10) continue;
            float markW = (deg % 20 == 0) ? 40 : 20;
            glBegin(GL_LINES);
            glVertex2f(cx - markW, markY);
            glVertex2f(cx + markW, markY);
            glEnd();

            // Números nos graus maiores
            if (deg % 20 == 0) {
                glEnable(GL_TEXTURE_2D);
                glColor4f(1f, 1f, 1f, 0.4f);
                textRenderer.renderText(String.valueOf(Math.abs(deg)), cx + markW + 5, markY - 4, 16);
                glDisable(GL_TEXTURE_2D);
            }
        }

        // ── Marca central (avião) ────────────────────────────────
        glColor4f(1f, 0.8f, 0f, 0.9f);
        glLineWidth(2.5f);
        glBegin(GL_LINES);
        glVertex2f(cx - 35, cy); glVertex2f(cx - 12, cy);
        glVertex2f(cx - 12, cy); glVertex2f(cx - 12, cy + 8);
        glVertex2f(cx + 12, cy); glVertex2f(cx + 35, cy);
        glVertex2f(cx + 12, cy); glVertex2f(cx + 12, cy + 8);
        glEnd();
        // Ponto central
        glPointSize(4f);
        glBegin(GL_POINTS);
        glVertex2f(cx, cy);
        glEnd();
        glPointSize(1f);
        glLineWidth(1f);

        // ── Indicadores de bank no arco superior ─────────────────
        glColor4f(1f, 1f, 1f, 0.35f);
        for (int deg : new int[]{-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60}) {
            double a = Math.toRadians(-90 + deg);
            float innerR = size - 8;
            float outerR = size - (deg % 30 == 0 ? 18 : 12);
            glBegin(GL_LINES);
            glVertex2f(cx + (float)Math.cos(a) * outerR, cy + (float)Math.sin(a) * outerR);
            glVertex2f(cx + (float)Math.cos(a) * innerR, cy + (float)Math.sin(a) * innerR);
            glEnd();
        }

        // Indicador de bank atual
        double bankA = Math.toRadians(-90 - roll);
        float triR = size - 4;
        glColor4f(1f, 0.8f, 0f, 0.9f);
        glBegin(GL_TRIANGLES);
        float bx = cx + (float)Math.cos(bankA) * triR;
        float by = cy + (float)Math.sin(bankA) * triR;
        glVertex2f(bx, by);
        glVertex2f(bx - 5, by - 10);
        glVertex2f(bx + 5, by - 10);
        glEnd();

        // ── Borda circular ───────────────────────────────────────
        glColor4f(0.5f, 0.52f, 0.65f, 0.5f);
        glLineWidth(2f);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < 64; i++) {
            double a = Math.PI * 2 * i / 64;
            glVertex2f(cx + (float)Math.cos(a) * size, cy + (float)Math.sin(a) * size);
        }
        glEnd();
        glLineWidth(1f);

        // Label
        glEnable(GL_TEXTURE_2D);
        glColor4f(0.5f, 0.52f, 0.65f, 0.6f);
        textRenderer.renderText("ATTITUDE", cx - 30, cy - size - 18, 16);
    }
    
    private void drawCompassRose(float cx, float cy, FlyByWire.FlightData data) {
        setup2DLegado();
        glDisable(GL_TEXTURE_2D);

        float radius = 60f;
        double heading = data.yaw % 360;
        if (heading < 0) heading += 360;

        // Fundo
        glColor4f(0.02f, 0.02f, 0.04f, 0.8f);
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(cx, cy);
        for (int i = 0; i <= 64; i++) {
            double a = Math.PI * 2 * i / 64;
            glVertex2f(cx + (float)Math.cos(a) * radius, cy + (float)Math.sin(a) * radius);
        }
        glEnd();

        // Marcas de graus — rotacionadas pelo heading
        for (int deg = 0; deg < 360; deg += 10) {
            double a = Math.toRadians(deg - heading - 90);
            float innerR = (deg % 30 == 0) ? radius - 18 : radius - 10;
            float outerR = radius - 4;

            if (deg % 30 == 0)
                glColor4f(0.7f, 0.72f, 0.85f, 0.6f);
            else
                glColor4f(0.4f, 0.42f, 0.55f, 0.3f);

            glBegin(GL_LINES);
            glVertex2f(cx + (float)Math.cos(a) * innerR, cy + (float)Math.sin(a) * innerR);
            glVertex2f(cx + (float)Math.cos(a) * outerR, cy + (float)Math.sin(a) * outerR);
            glEnd();
        }

        // Labels N S E W
        glEnable(GL_TEXTURE_2D);
        String[] dirs = {"N", "E", "S", "W"};
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(i * 90 - heading - 90);
            float lx = cx + (float)Math.cos(a) * (radius - 28);
            float ly = cy + (float)Math.sin(a) * (radius - 28);
            if (i == 0) // North = vermelho
                glColor4f(0.9f, 0.3f, 0.3f, 0.8f);
            else
                glColor4f(0.7f, 0.72f, 0.85f, 0.7f);
            textRenderer.renderText(dirs[i], lx - 4, ly - 6, 16);
        }

        // Triângulo indicador fixo no topo
        glDisable(GL_TEXTURE_2D);
        glColor4f(1f, 0.8f, 0f, 0.9f);
        glBegin(GL_TRIANGLES);
        glVertex2f(cx, cy - radius + 2);
        glVertex2f(cx - 5, cy - radius - 8);
        glVertex2f(cx + 5, cy - radius - 8);
        glEnd();

        // Borda
        glColor4f(0.5f, 0.52f, 0.65f, 0.4f);
        glLineWidth(1.5f);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < 64; i++) {
            double a = Math.PI * 2 * i / 64;
            glVertex2f(cx + (float)Math.cos(a) * radius, cy + (float)Math.sin(a) * radius);
        }
        glEnd();
        glLineWidth(1f);

        // Heading digital
        glEnable(GL_TEXTURE_2D);
        glColor4f(0.5f, 0.52f, 0.65f, 0.5f);
        textRenderer.renderText("HDG", cx - 14, cy - radius - 22, 16);
        glColor4f(0.9f, 0.92f, 1f, 0.9f);
        textRenderer.renderText(String.format("%03.0f", heading), cx - 14, cy + radius + 8, 20);
    }
    
    private void drawVSI(float x, float y, FlyByWire.FlightData data) {
        setup2DLegado();
        glDisable(GL_TEXTURE_2D);

        float w = 40, h = 100;

        glColor4f(0.02f, 0.02f, 0.04f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(x, y); glVertex2f(x + w, y);
        glVertex2f(x + w, y + h); glVertex2f(x, y + h);
        glEnd();

        glColor4f(0.5f, 0.52f, 0.65f, 0.3f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y); glVertex2f(x + w, y);
        glVertex2f(x + w, y + h); glVertex2f(x, y + h);
        glEnd();

        float centerY = y + h / 2;
        glColor4f(0.5f, 0.52f, 0.65f, 0.4f);
        glBegin(GL_LINES);
        glVertex2f(x + 3, centerY); glVertex2f(x + w - 3, centerY);
        glEnd();

        // Marcas
        glColor4f(0.4f, 0.42f, 0.55f, 0.3f);
        for (int i = -3; i <= 3; i++) {
            if (i == 0) continue;
            float markY = centerY - i * (h / 8);
            float markW = (i % 2 == 0) ? 12 : 6;
            glBegin(GL_LINES);
            glVertex2f(x + w - markW - 3, markY);
            glVertex2f(x + w - 3, markY);
            glEnd();
        }

        double vertSpeed = Math.sin(Math.toRadians(data.pitch)) * data.speed * 60 * 3.0;
        float maxVS = 10000;
        float normalized = (float)(vertSpeed / maxVS);
        normalized = Math.max(-1, Math.min(1, normalized));

        float barH = normalized * (h / 2 - 8);
        if (normalized > 0) {
            glColor4f(0.3f, 0.8f, 0.5f, 0.6f);
            glBegin(GL_QUADS);
            glVertex2f(x + 5, centerY - barH);
            glVertex2f(x + 20, centerY - barH);
            glVertex2f(x + 20, centerY);
            glVertex2f(x + 5, centerY);
            glEnd();
        } else {
            glColor4f(0.8f, 0.3f, 0.3f, 0.6f);
            glBegin(GL_QUADS);
            glVertex2f(x + 5, centerY);
            glVertex2f(x + 20, centerY);
            glVertex2f(x + 20, centerY - barH);
            glVertex2f(x + 5, centerY - barH);
            glEnd();
        }

        glEnable(GL_TEXTURE_2D);
        glColor4f(0.5f, 0.52f, 0.65f, 0.5f);
        textRenderer.renderText("VS", x + 8, y - 14, 16);
        glColor4f(0.85f, 0.87f, 0.95f, 0.85f);
        textRenderer.renderText(String.format("%+.0f", vertSpeed), x - 5, y + h + 4, 16);
    }

    // ── RADAR TÁTICO ──────────────────────────────────────────────────
    private void drawRadar(float cx, float cy, float radius) {
        setup2DLegado();
        glDisable(GL_TEXTURE_2D);

        // Fundo
        glColor4f(0.02f, 0.03f, 0.02f, 0.8f);
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(cx, cy);
        for (int i = 0; i <= 64; i++) {
            double a = Math.PI * 2 * i / 64;
            glVertex2f(cx + (float)Math.cos(a) * radius, cy + (float)Math.sin(a) * radius);
        }
        glEnd();

        // Círculos de alcance
        float[] rings = {0.33f, 0.66f, 1f};
        for (float r : rings) {
            glColor4f(0.2f, 0.4f, 0.25f, 0.2f);
            glBegin(GL_LINE_LOOP);
            for (int i = 0; i < 64; i++) {
                double a = Math.PI * 2 * i / 64;
                glVertex2f(cx + (float)Math.cos(a) * radius * r,
                           cy + (float)Math.sin(a) * radius * r);
            }
            glEnd();
        }

        // Grade
        glColor4f(0.2f, 0.4f, 0.25f, 0.15f);
        glBegin(GL_LINES);
        glVertex2f(cx - radius, cy); glVertex2f(cx + radius, cy);
        glVertex2f(cx, cy - radius); glVertex2f(cx, cy + radius);
        glEnd();

        // Sweep animado
        float sweep = crtTime * 50f;
        glColor4f(0.2f, 0.8f, 0.3f, 0.2f);
        glLineWidth(2f);
        glBegin(GL_LINES);
        glVertex2f(cx, cy);
        glVertex2f(cx + (float)Math.cos(Math.toRadians(sweep)) * radius,
                   cy + (float)Math.sin(Math.toRadians(sweep)) * radius);
        glEnd();
        // Trail
        for (int i = 1; i <= 6; i++) {
            float ta = sweep - i * 5f;
            glColor4f(0.15f, 0.6f, 0.25f, Math.max(0, 0.12f - i * 0.02f));
            glBegin(GL_LINES);
            glVertex2f(cx, cy);
            glVertex2f(cx + (float)Math.cos(Math.toRadians(ta)) * radius,
                       cy + (float)Math.sin(Math.toRadians(ta)) * radius);
            glEnd();
        }
        glLineWidth(1f);

        // Avião no centro
        glColor4f(0.3f, 1f, 0.4f, 0.9f);
        glPointSize(4f);
        glBegin(GL_POINTS);
        glVertex2f(cx, cy);
        glEnd();

        // Inimigo
        if (enemy != null) {
            Vector3f myPos  = fbw.getPosicao();
            Vector3f enyPos = enemy.getPosition();
            if (enyPos != null) {
                float scale = radius / 50000f;
                float ex = cx + (enyPos.x - myPos.x) * scale;
                float ey = cy + (enyPos.z - myPos.z) * scale;
                if (Math.hypot(ex - cx, ey - cy) < radius) {
                    // Blip pulsante
                    float blip = 0.7f + (float)(Math.sin(crtTime * 5) * 0.3);
                    glColor4f(1f, 0.3f, 0.2f, blip);
                    glPointSize(5f);
                    glBegin(GL_POINTS);
                    glVertex2f(ex, ey);
                    glEnd();
                }
            }
        }
        glPointSize(1f);

        // Borda
        glColor4f(0.3f, 0.6f, 0.35f, 0.4f);
        glLineWidth(1.5f);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < 64; i++) {
            double a = Math.PI * 2 * i / 64;
            glVertex2f(cx + (float)Math.cos(a) * radius, cy + (float)Math.sin(a) * radius);
        }
        glEnd();
        glLineWidth(1f);

        // Label
        glEnable(GL_TEXTURE_2D);
        glColor4f(0.3f, 0.65f, 0.35f, 0.5f);
        textRenderer.renderText("RADAR", cx - 18, cy - radius - 16, 16);
        glColor4f(0.25f, 0.5f, 0.3f, 0.35f);
        textRenderer.renderText("50km", cx + radius - 25, cy + radius + 4, 16);
    }

    // ── SISTEMAS ──────────────────────────────────────────────────────
    private void drawSystemsPanel(FlyByWire.FlightData data) {
        setup2DLegado();
        float px = 30, py = 550;

        glEnable(GL_TEXTURE_2D);
        glColor4f(0.5f, 0.52f, 0.65f, 0.5f);
        textRenderer.renderText("SYSTEMS", px, py - 5, 16);

        drawModernBar(px, py + 12, 160, 10, (float)(fbw.getEngineTemp() / 100.0),
            "ENG", fbw.getEngineTemp() > 80);
        drawModernBar(px, py + 34, 160, 10, (float)(fbw.getFuel() / 100000.0),
            "FUEL", fbw.getFuel() / 100000.0 < 0.2);
        drawModernBar(px, py + 56, 160, 10, (float)(fbw.getThrottle() / 6000.0),
            "THR", false);

        float machPct = (float)Math.min(data.mach / 3.5, 1.0);
        glColor4f(0.3f + machPct * 0.7f, 0.9f - machPct * 0.4f, 0.4f - machPct * 0.3f, 0.9f);
        textRenderer.renderText(String.format("MACH %.2f", data.mach), px, py + 78, 20);
    }

    private void drawMissionInfo(float x, float y, FlyByWire.FlightData data) {
        setup2DLegado();

        Mission current = missionManager.currentMission();

        // Fundo do painel
        glDisable(GL_TEXTURE_2D);
        glColor4f(0.02f, 0.02f, 0.04f, 0.75f);
        glBegin(GL_QUADS);
        glVertex2f(x - 10, y - 10);
        glVertex2f(x + 260, y - 10);
        glVertex2f(x + 260, y + 260);
        glVertex2f(x - 10, y + 260);
        glEnd();

        // Borda
        float borderPulse = 0.3f + (float)(Math.sin(crtTime * 2.0) * 0.05);
        glColor4f(0.4f, 0.42f, borderPulse + 0.3f, 0.4f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x - 10, y - 10);
        glVertex2f(x + 260, y - 10);
        glVertex2f(x + 260, y + 260);
        glVertex2f(x - 10, y + 260);
        glEnd();

        glEnable(GL_TEXTURE_2D);

        // Header
        glColor4f(0.5f, 0.52f, 0.65f, 0.6f);
        textRenderer.renderText("MISSION DATA", x, y, 16);

        glDisable(GL_TEXTURE_2D);
        glColor4f(0.4f, 0.42f, 0.55f, 0.3f);
        glBegin(GL_LINES);
        glVertex2f(x, y + 16); glVertex2f(x + 250, y + 16);
        glEnd();
        glEnable(GL_TEXTURE_2D);

        float cy = y + 30;

        if (current == null || current.getState() != Mission.State.ACTIVE) {
            glColor4f(0.4f, 0.42f, 0.5f, 0.5f);
            textRenderer.renderText("NO ACTIVE MISSION", x, cy, 20);
            textRenderer.renderText("Free flight mode", x, cy + 28, 16);
            cy += 70;
            drawFuelEstimate(x, cy, data);
            return;
        }

        // Nome
        glColor4f(0.9f, 0.92f, 1f, 0.95f);
        textRenderer.renderText(current.getName(), x, cy, 20);
        cy += 26;

        // Recon
        if (current instanceof fbw.gameplay.MissionRecon recon) {
            Vector3f tgt = recon.getTarget();
            Vector3f pos = fbw.getPosicao();
            float dx = tgt.x - pos.x;
            float dz = tgt.z - pos.z;
            float dist = (float)Math.sqrt(dx * dx + dz * dz);

            double tgtBearing = Math.toDegrees(Math.atan2(dx, dz));
            if (tgtBearing < 0) tgtBearing += 360;
            double hdg = data.yaw % 360;
            if (hdg < 0) hdg += 360;
            double diff = tgtBearing - hdg;
            if (diff > 180) diff -= 360;
            if (diff < -180) diff += 360;

            String dirLabel = Math.abs(diff) < 5 ? "NOSE" :
                              diff > 0 ? String.format("R%.0f", Math.abs(diff)) :
                              String.format("L%.0f", Math.abs(diff));

            glColor4f(0.5f, 0.52f, 0.6f, 0.6f);
            textRenderer.renderText("TGT", x, cy, 16);
            glColor4f(0.9f, 0.7f, 0.4f, 0.9f);
            textRenderer.renderText(String.format("%.1fkm %s", dist / 1000f, dirLabel), x + 40, cy, 16);
            cy += 22;

            // Barra de captura
            glColor4f(0.5f, 0.52f, 0.6f, 0.6f);
            textRenderer.renderText("CAP", x, cy, 16);

            float progress = recon.getCaptureProgress();
            float required = recon.getRequired();
            float capturePct = Math.min(1f, progress / required);

            glDisable(GL_TEXTURE_2D);
            float barX = x + 40, barW = 170, barH = 12;
            glColor4f(0.06f, 0.06f, 0.08f, 0.8f);
            glBegin(GL_QUADS);
            glVertex2f(barX, cy + 2); glVertex2f(barX + barW, cy + 2);
            glVertex2f(barX + barW, cy + 2 + barH); glVertex2f(barX, cy + 2 + barH);
            glEnd();
            if (capturePct > 0) {
                float fillW = barW * capturePct;
                glBegin(GL_QUADS);
                glColor4f(0.2f, 0.7f, 0.3f, 0.7f);
                glVertex2f(barX, cy + 2); glVertex2f(barX, cy + 2 + barH);
                glColor4f(0.3f, 0.9f, 0.4f, 0.8f);
                glVertex2f(barX + fillW, cy + 2 + barH); glVertex2f(barX + fillW, cy + 2);
                glEnd();
            }
            glColor4f(0.4f, 0.42f, 0.55f, 0.3f);
            glBegin(GL_LINE_LOOP);
            glVertex2f(barX, cy + 2); glVertex2f(barX + barW, cy + 2);
            glVertex2f(barX + barW, cy + 2 + barH); glVertex2f(barX, cy + 2 + barH);
            glEnd();
            glEnable(GL_TEXTURE_2D);
            glColor4f(0.8f, 0.82f, 0.9f, 0.7f);
            textRenderer.renderText(String.format("%.0f%%", capturePct * 100), barX + barW + 6, cy, 16);
            cy += 22;

            // Speed check
            glColor4f(0.5f, 0.52f, 0.6f, 0.6f);
            textRenderer.renderText("SPD", x, cy, 16);
            float minSpd = recon.getMinSpeed();
            boolean speedOk = data.speed >= minSpd;
            if (speedOk)
                glColor4f(0.3f, 0.9f, 0.4f, 0.9f);
            else
                glColor4f(1f, 0.4f, 0.3f, 0.9f);
            textRenderer.renderText(speedOk ? "OK" : String.format("NEED %.0f+", minSpd), x + 40, cy, 16);
            cy += 22;

        } else if (current instanceof fbw.gameplay.MissionEscape escape) {
            glColor4f(0.5f, 0.52f, 0.6f, 0.6f);
            textRenderer.renderText("MACH REQ", x, cy, 16);
            float targetMach = escape.getTargetMach();
            boolean machOk = data.mach >= targetMach;
            if (machOk)
                glColor4f(0.3f, 0.9f, 0.4f, 0.9f);
            else
                glColor4f(1f, 0.4f, 0.3f, 0.9f);
            textRenderer.renderText(String.format("%.1f/%.1f %s",
                data.mach, targetMach, machOk ? "OK" : "LOW"), x + 90, cy, 16);
            cy += 22;

        } else if (current instanceof fbw.gameplay.MissionFlightWindow window) {
            glColor4f(0.5f, 0.52f, 0.6f, 0.6f);
            textRenderer.renderText("ENVELOPE", x, cy, 16);
            glColor4f(0.8f, 0.82f, 0.9f, 0.8f);
            textRenderer.renderText(String.format("ALT %.0f-%.0fft",
                window.getMinAlt(), window.getMaxAlt()), x, cy + 18, 16);
            textRenderer.renderText(String.format("MACH %.1f-%.1f",
                window.getMinMach(), window.getMaxMach()), x, cy + 36, 16);
            cy += 55;
        }

        cy += 10;
        drawFuelEstimate(x, cy, data);
    }

    private void drawFuelEstimate(float x, float y, FlyByWire.FlightData data) {
        setup2DLegado();
        glEnable(GL_TEXTURE_2D);

        double fuel = fbw.getFuel();
        double throttle = fbw.getThrottle();
        double burnRate = 50.0 * (throttle / 1000.0 + 0.5); // mesma fórmula do FlyByWire
        double secondsLeft = (burnRate > 0) ? fuel / burnRate : 99999;
        int minsLeft = (int)(secondsLeft / 60);

        glColor4f(0.5f, 0.52f, 0.6f, 0.6f);
        textRenderer.renderText("FUEL TIME", x, y, 16);

        if (minsLeft < 5)
            glColor4f(1f, 0.3f, 0.2f, 0.9f);
        else if (minsLeft < 15)
            glColor4f(1f, 0.8f, 0.3f, 0.9f);
        else
            glColor4f(0.3f, 0.9f, 0.4f, 0.9f);

        if (minsLeft > 120)
            textRenderer.renderText(String.format("%dh %02dm", minsLeft / 60, minsLeft % 60), x + 100, y, 20);
        else
            textRenderer.renderText(String.format("%d min", minsLeft), x + 100, y, 20);
    }
    
    private void drawModernBar(float x, float y, float w, float h,
                               float fill, String label, boolean warning) {
        glDisable(GL_TEXTURE_2D);
        fill = Math.max(0, Math.min(1, fill));

        // Fundo
        glColor4f(0.06f, 0.06f, 0.08f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(x, y); glVertex2f(x + w, y);
        glVertex2f(x + w, y + h); glVertex2f(x, y + h);
        glEnd();

        // Borda fina
        glColor4f(0.3f, 0.32f, 0.4f, 0.3f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y); glVertex2f(x + w, y);
        glVertex2f(x + w, y + h); glVertex2f(x, y + h);
        glEnd();

        // Preenchimento com gradiente
        float fillW = w * fill;
        if (warning && ((int)(crtTime * 4)) % 2 == 0) {
            glColor4f(0.9f, 0.2f, 0.1f, 0.8f);
        } else {
            glBegin(GL_QUADS);
            glColor4f(0.2f, 0.5f, 0.7f, 0.7f);
            glVertex2f(x, y); glVertex2f(x, y + h);
            glColor4f(0.3f + fill * 0.5f, 0.6f - fill * 0.3f, 0.8f - fill * 0.5f, 0.8f);
            glVertex2f(x + fillW, y + h); glVertex2f(x + fillW, y);
            glEnd();

            // Highlight no topo
            glColor4f(0.5f, 0.7f, 0.9f, 0.2f);
            glBegin(GL_QUADS);
            glVertex2f(x, y); glVertex2f(x + fillW, y);
            glVertex2f(x + fillW, y + 2); glVertex2f(x, y + 2);
            glEnd();
        }

        if (warning && ((int)(crtTime * 4)) % 2 == 0) {
            glBegin(GL_QUADS);
            glVertex2f(x, y); glVertex2f(x + fillW, y);
            glVertex2f(x + fillW, y + h); glVertex2f(x, y + h);
            glEnd();
        }

        // Label
        glEnable(GL_TEXTURE_2D);
        if (warning && ((int)(crtTime * 4)) % 2 == 0)
            glColor4f(1f, 0.3f, 0.2f, 0.9f);
        else
            glColor4f(0.6f, 0.62f, 0.7f, 0.7f);
        textRenderer.renderText(label, x + w + 8, y - 1, 16);

        // Percentagem
        glColor4f(0.8f, 0.82f, 0.9f, 0.6f);
        textRenderer.renderText(String.format("%.0f%%", fill * 100), x + w - 35, y - 1, 16);
    }

    // ── GRÁFICO DE VELOCIDADE ─────────────────────────────────────────
    private void drawSpeedGraph(float x, float y, FlyByWire.FlightData data) {
        setup2DLegado();
        glDisable(GL_TEXTURE_2D);

        float w = 180, h = 70;

        // Fundo
        glColor4f(0.02f, 0.02f, 0.04f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(x, y); glVertex2f(x + w, y);
        glVertex2f(x + w, y + h); glVertex2f(x, y + h);
        glEnd();

        // Grade sutil
        glColor4f(0.15f, 0.15f, 0.2f, 0.2f);
        glBegin(GL_LINES);
        for (int i = 1; i < 4; i++) {
            float gy = y + (h / 4f) * i;
            glVertex2f(x, gy); glVertex2f(x + w, gy);
        }
        glEnd();

        // Área preenchida
        double[] hist = fbw.getSpeedHistory();
        int start = fbw.getHistoryIndex();
        glBegin(GL_QUAD_STRIP);
        for (int i = 0; i < 60; i++) {
            double spd = hist[(start + i) % 60];
            float px2 = x + (i / 59f) * w;
            float py2 = y + h - (float)(spd / 6000.0) * h;
            glColor4f(0.15f, 0.4f, 0.6f, 0.15f);
            glVertex2f(px2, y + h);
            glColor4f(0.2f, 0.5f, 0.7f, 0.25f);
            glVertex2f(px2, py2);
        }
        glEnd();

        // Linha
        glColor4f(0.4f, 0.8f, 0.95f, 0.8f);
        glLineWidth(1.5f);
        glBegin(GL_LINE_STRIP);
        for (int i = 0; i < 60; i++) {
            double spd = hist[(start + i) % 60];
            float px2 = x + (i / 59f) * w;
            float py2 = y + h - (float)(spd / 6000.0) * h;
            glVertex2f(px2, py2);
        }
        glEnd();
        glLineWidth(1f);

        // Ponto atual
        double currentSpd = hist[(start + 59) % 60];
        float dotY = y + h - (float)(currentSpd / 6000.0) * h;
        glColor4f(0.5f, 0.9f, 1f, 0.9f);
        glPointSize(4f);
        glBegin(GL_POINTS);
        glVertex2f(x + w, dotY);
        glEnd();
        glPointSize(1f);

        // Borda
        glColor4f(0.3f, 0.32f, 0.4f, 0.3f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y); glVertex2f(x + w, y);
        glVertex2f(x + w, y + h); glVertex2f(x, y + h);
        glEnd();

        glEnable(GL_TEXTURE_2D);
        glColor4f(0.5f, 0.52f, 0.65f, 0.5f);
        textRenderer.renderText("SPEED (60s)", x, y - 14, 16);
    }

    // ── G-FORCE ───────────────────────────────────────────────────────
    private void drawGForce() {
        setup2DLegado();
        double g = fbw.getGForce();

        if (g > 4.0) {
            float intensity = (float) Math.min((g - 4.0) / 5.0, 0.5);
            glDisable(GL_TEXTURE_2D);
            glColor4f(0.8f, 0f, 0f, intensity);
            glBegin(GL_QUADS);
            glVertex2f(0, 0);    glVertex2f(1280, 0);
            glVertex2f(1280, 60); glVertex2f(0, 60);
            glEnd();
            glBegin(GL_QUADS);
            glVertex2f(0, 660);    glVertex2f(1280, 660);
            glVertex2f(1280, 720); glVertex2f(0, 720);
            glEnd();
        }

        glEnable(GL_TEXTURE_2D);
        if (g > 6)
            glColor4f(1f, 0.2f, 0.2f, 1f);
        else if (g > 4)
            glColor4f(1f, 0.7f, 0.2f, 0.9f);
        else
            glColor4f(0.5f, 0.52f, 0.65f, 0.7f);
        textRenderer.renderText(String.format("%.1fG", g), 600, 680, 24);
    }

    private void renderExternal() {
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;
        renderWithPostProcess(0.016f);
    }
    
    private void renderExternalScene() {
        FlyByWire.FlightData data = fbw.getLastData();
        glClearColor(0f, 0f, 0f, 1f);

        if (data != null) {
            camera.updateNearFarForAltitude((float)data.altitude);
        }
        
        Vector3f sunDir = new Vector3f(0.8f, 0.4f, 0.3f).normalize();
        FlyByWire.FlightData skyData = fbw.getLastData();
        float alt = (skyData != null) ? (float)skyData.altitude : 0f;
        skyRenderer.render(camera, sunDir, alt);
        renderStars(alt);

        glEnable(GL_DEPTH_TEST);

        float lx = 0.8f, ly = 0.4f, lz = 0.3f;
        Vector3f posReal  = fbw.getPosicao();
        Vector3f planePos = new Vector3f(posReal.x, posReal.y, posReal.z);
        
        if (!cameraFixedMode) {
            camera.setMode(2, planePos);
        }

        shader.bind();
        shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
        shader.setUniformMatrix4f("view",       camera.getViewMatrix());
        shader.setUniform3f("viewPos", camera.getPosition().x,
                camera.getPosition().y,
                camera.getPosition().z);
        shader.setUniform3f("lightDir",   -lx, -ly, -lz);
        shader.setUniform3f("lightColor", 1.4f, 1.3f, 1.1f); 
        shader.setUniform3f("skyColor",    0.15f, 0.45f, 0.85f);
        shader.setUniform3f("rimColor",    0.8f, 0.85f, 1.0f);
        shader.setUniformFloat("rimStrength", 2.0f);
        shader.setUniformFloat("fogDensity", 0.0000002f);
        shader.setUniform3f("emissiveColor",       0f, 0f, 0f);
        shader.setUniformFloat("emissiveStrength", 0f);
        shader.setUniform3f("sunDir", sunDir.x, sunDir.y, sunDir.z);

     // ── TERRENO HEIGHTMAP ──────────────────────────────────
        shader.setUniform1i("useTexture", 1);
        shader.setUniform1i("useTerrain", 0);
        shader.setUniform1i("useSatellite", 1);
        shader.setUniform1i("texture1", 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, satelliteTextureId);

        shader.setUniformMatrix4f("model", new Matrix4f());
        heightmapTerrain.render();

        glBindTexture(GL_TEXTURE_2D, 0);
        shader.setUniform1i("useSatellite", 0);

     // ── SR-71 ─────────────────────────────────────────────────────
        shader.setUniform1i("useTexture", 1);
        shader.setUniform1i("texture1", 0);
        shader.setUniform3f("lightColor", 1.0f, 1.0f, 1.0f);

        // Usa o vetor de direção real — modelo sempre aponta pra onde voa
        Vector3f fwd = new Vector3f(fbw.getDirecao()).normalize();
        Vector3f up  = new Vector3f(0, 1, 0);

        // Evita gimbal lock se apontar direto pra cima/baixo
        if (Math.abs(fwd.dot(up)) > 0.99f) {
            up.set(0, 0, 1);
        }

        Matrix4f modelMatrix = new Matrix4f()
            .translate(planePos)
            .rotateTowards(fwd, up)                          // aponta na direção de voo
            .rotateZ((float) Math.toRadians(data.roll))     // aplica roll visual
            .rotateX((float) Math.toRadians(0))
            .rotateZ((float) Math.toRadians(0))          
            .scale(5.0f);

        shader.setUniformMatrix4f("model", modelMatrix);
        for (Model m : models) {
            if (m.getTextureId() > 0) {
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, m.getTextureId());
            }
            m.render();
        }
        glBindTexture(GL_TEXTURE_2D, 0);

        shader.unbind();

        renderTargetMarker();
        
        renderSun();
        renderClouds();

        // ── ÁRVORES ───────────────────────────────────────────────────
//        shader.bind();
//        shader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
//        shader.setUniformMatrix4f("view",       camera.getViewMatrix());
//        shader.setUniform1i("useTexture",  0);
//        shader.setUniform1i("useTerrain",  0);
//        shader.setUniform3f("lightDir",   -0.5f, -0.7f, -0.2f);
//        shader.setUniform3f("lightColor",  2.0f, 1.8f, 1.4f);
//        shader.setUniform3f("viewPos",     camera.getPosition().x,
//                                           camera.getPosition().y,
//                                           camera.getPosition().z);
//        shader.setUniform3f("rimColor",    0.3f, 0.6f, 0.2f);
//        shader.setUniformFloat("rimStrength",      0.3f);
//        shader.setUniformFloat("fogDensity",       0.000000000012f);
//        shader.setUniform3f("emissiveColor",       0f, 0f, 0f);
//        shader.setUniformFloat("emissiveStrength", 0f);
//        shader.setUniform3f("sunDir",      sunDir.x, sunDir.y, sunDir.z);
//        shader.setUniform3f("skyColor",    0.15f, 0.45f, 0.85f);
//        treeSystem.render(camera, shader);
//        shader.unbind();

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

    private void renderCameraScreen() {
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        Vector3f posReal  = fbw.getPosicao();
        Vector3f planePos = new Vector3f(posReal);

        float yawRad = (float) Math.toRadians(data.yaw);


        float forwardOffset = 15f; 
        Vector3f fixedCamPos = new Vector3f(
            planePos.x + (float) Math.sin(yawRad) * forwardOffset,
            planePos.y,
            planePos.z + (float) Math.cos(yawRad) * forwardOffset
        );

        Vector3f groundTarget = new Vector3f(
            fixedCamPos.x,
            0f,
            fixedCamPos.z
        );

        if (!cameraFixedMode) {
            camera.setMode(2, planePos);
            camera.updateFovForSpeed(data.speed, 6000, 1280f / 720f);
        }
        
        cameraFixedMode = true;
        camera.setDownwardCam(fixedCamPos, groundTarget, (float) data.yaw);

        sceneBuffer.bind();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        renderExternalScene();
        sceneBuffer.unbind();

        cameraFixedMode = false;
        camera.clearDownwardCam();

        // Composite com tint verde de câmera de reconhecimento
        setup2DLegado();
        glClearColor(0f, 0f, 0f, 1f);
        glColor4f(0.85f, 1f, 0.85f, 1f); // leve tint esverdeado
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, sceneBuffer.getTexture());
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1); glVertex2f(0,    0);
        glTexCoord2f(1, 1); glVertex2f(1280, 0);
        glTexCoord2f(1, 0); glVertex2f(1280, 720);
        glTexCoord2f(0, 0); glVertex2f(0,    720);
        glEnd();
        glBindTexture(GL_TEXTURE_2D, 0);

        // Scanlines mais intensas na câmera
        glDisable(GL_TEXTURE_2D);
        glColor4f(0f, 0f, 0f, 0.35f);
        glBegin(GL_LINES);
        for (int y = 0; y < 720; y += 3) {
            glVertex2f(0, y); glVertex2f(1280, y);
        }
        glEnd();

        // Moldura de câmera — cantos escuros (vinheta circular)
        // Bordas pretas
        glColor4f(0f, 0f, 0f, 0.6f);
        // Topo e baixo
        glBegin(GL_QUADS);
        glVertex2f(0,0);    glVertex2f(1280,0);
        glVertex2f(1280,60); glVertex2f(0,60);
        glEnd();
        glBegin(GL_QUADS);
        glVertex2f(0,660);    glVertex2f(1280,660);
        glVertex2f(1280,720); glVertex2f(0,720);
        glEnd();
        // Laterais
        glBegin(GL_QUADS);
        glVertex2f(0,0);   glVertex2f(80,0);
        glVertex2f(80,720); glVertex2f(0,720);
        glEnd();
        glBegin(GL_QUADS);
        glVertex2f(1200,0);   glVertex2f(1280,0);
        glVertex2f(1280,720); glVertex2f(1200,720);
        glEnd();

        // Retículo central
        glColor4f(0f, 1f, 0.3f, 0.8f);
        glLineWidth(1.5f);
        float cx = 640, cy = 360;
        // Cruz central
        glBegin(GL_LINES);
        glVertex2f(cx - 40, cy); glVertex2f(cx - 10, cy);
        glVertex2f(cx + 10, cy); glVertex2f(cx + 40, cy);
        glVertex2f(cx, cy - 40); glVertex2f(cx, cy - 10);
        glVertex2f(cx, cy + 10); glVertex2f(cx, cy + 40);
        glEnd();
        // Quadrado do retículo
        glBegin(GL_LINE_LOOP);
        glVertex2f(cx - 60, cy - 40);
        glVertex2f(cx + 60, cy - 40);
        glVertex2f(cx + 60, cy + 40);
        glVertex2f(cx - 60, cy + 40);
        glEnd();
        glLineWidth(1f);

        // Indicador de alvo no radar da câmera
        Vector3f myPos = fbw.getPosicao();
        fbw.gameplay.Mission current = missionManager.currentMission();
        if (current instanceof fbw.gameplay.MissionRecon recon) {
            Vector3f target = recon.getTarget();
            float dx = target.x - myPos.x;
            float dz = target.z - myPos.z;

            // Normaliza para pixels — raio de 40km = metade da tela
            float scale = 500f / 40000f;
            float tx = cx + dx * scale;
            float ty = cy + dz * scale;
            tx = Math.max(90, Math.min(1190, tx));
            ty = Math.max(70, Math.min(650, ty));

            glColor4f(1f, 0.3f, 0.3f, 0.9f);
            glPointSize(8f);
            glBegin(GL_POINTS);
            glVertex2f(tx, ty);
            glEnd();
            glPointSize(1f);

            // Seta apontando para o alvo se fora do retículo
            float dist = (float) Math.sqrt(dx*dx + dz*dz);
            glEnable(GL_TEXTURE_2D);
            glColor4f(1f, 0.3f, 0.3f, 0.9f);
            textRenderer.renderText(String.format("TGT %.0fkm", dist / 1000f), 100, 680);
            glDisable(GL_TEXTURE_2D);
        }

        // HUD da câmera
        glEnable(GL_TEXTURE_2D);
        glColor4f(0f, 1f, 0.3f, 0.9f);
        textRenderer.renderText("OPTICAL RECONNAISSANCE SYSTEM", 380, 20);
        textRenderer.renderText(String.format("ALT: %.0f ft", data.altitude), 100, 40);
        textRenderer.renderText(String.format("MACH: %.2f", data.mach), 400, 40);
        textRenderer.renderText("LMB = CAPTURE   F = EXIT", 480, 698);

        // Flash ao fotografar
        if (cameraFlashTimer > 0) {
            cameraFlashTimer--;
            float flashAlpha = cameraFlashTimer / 8f;
            glDisable(GL_TEXTURE_2D);
            glColor4f(1f, 1f, 1f, flashAlpha);
            glBegin(GL_QUADS);
            glVertex2f(0,0); glVertex2f(1280,0);
            glVertex2f(1280,720); glVertex2f(0,720);
            glEnd();
        }

        // Popup de foto capturada
        if (photoTaken) {
            photoPopupTimer += lastDelta;
            if (photoPopupTimer < 3f) {
                float alpha = photoPopupTimer < 0.3f ? photoPopupTimer / 0.3f
                            : photoPopupTimer > 2.5f ? (3f - photoPopupTimer) / 0.5f : 1f;

                glDisable(GL_TEXTURE_2D);
                glColor4f(0f, 0f, 0f, alpha * 0.85f);
                glBegin(GL_QUADS);
                glVertex2f(440, 280); glVertex2f(840, 280);
                glVertex2f(840, 440); glVertex2f(440, 440);
                glEnd();

                glColor4f(0f, 1f, 0.3f, alpha);
                glLineWidth(2f);
                glBegin(GL_LINE_LOOP);
                glVertex2f(440, 280); glVertex2f(840, 280);
                glVertex2f(840, 440); glVertex2f(440, 440);
                glEnd();
                glLineWidth(1f);

                glEnable(GL_TEXTURE_2D);
                glColor4f(0f, 1f, 0.3f, alpha);
                textRenderer.renderText("PHOTO CAPTURED", 520, 320);
                glColor4f(0.8f, 0.8f, 0.8f, alpha);
                textRenderer.renderText("RECONNAISSANCE COMPLETE", 480, 355);
                textRenderer.renderText("TRANSMITTING TO BASE...", 490, 380);
                textRenderer.renderText("PHOTO QUALITY: EXCELLENT", 490, 405);
            } else {
                photoTaken = false;
            }
        }

        renderRSOPortrait(lastDelta);
    }
    
    private void renderHUD() {
        setup2DLegado();
        glEnable(GL_TEXTURE_2D);

        renderRSOPortrait(lastDelta);

        countermeasureActive = enemy.isMissileWarning();
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        // Offset de glitch global
        float gx = glitchOffsetX;
        float flickerAlpha = hudFlicker ? 0.4f : 1f;

        // ── PAINEL ESQUERDO — dados de voo ────────────────────────
        float lx = 30 + gx, ly = 30;

        // Fundo
        glDisable(GL_TEXTURE_2D);
        glColor4f(0f, 0.02f, 0f, 0.75f * flickerAlpha);
        glBegin(GL_QUADS);
        glVertex2f(lx - 10, ly - 10);
        glVertex2f(lx + 270, ly - 10);
        glVertex2f(lx + 270, ly + 195);
        glVertex2f(lx - 10, ly + 195);
        glEnd();

        // Borda com leve "breathing" (pulsa suavemente)
        float borderPulse = 0.4f + (float)(Math.sin(crtTime * 2.0) * 0.1);
        glColor4f(0f, borderPulse, 0f, 0.7f * flickerAlpha);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(lx - 10, ly - 10);
        glVertex2f(lx + 270, ly - 10);
        glVertex2f(lx + 270, ly + 195);
        glVertex2f(lx - 10, ly + 195);
        glEnd();

        glEnable(GL_TEXTURE_2D);

        // Header com cursor piscante
        String cursor = ((int)(crtTime * 3) % 2 == 0) ? "_" : " ";
        glColor4f(0f, 0.7f, 0.25f, flickerAlpha);
        textRenderer.renderText(">> FLIGHT DATA" + cursor, lx, ly);

        // Separador
        glColor4f(0f, 0.25f, 0f, flickerAlpha);
        textRenderer.renderText("========================", lx, ly + 16);

        // Altitude — pisca vermelho se muito baixo
        boolean altWarn = data.altitude < 5000;
        if (altWarn && ((int)(crtTime * 5)) % 2 == 0)
            glColor4f(1f, 0.2f, 0.1f, flickerAlpha);
        else
            glColor4f(0f, 0.9f, 0.3f, flickerAlpha);
        textRenderer.renderText(String.format("ALT  %,8.0f ft", data.altitude), lx, ly + 35);

        // Velocidade
        glColor4f(0f, 0.9f, 0.3f, flickerAlpha);
        textRenderer.renderText(String.format("SPD  %,8.0f kh", data.speed * 3.6), lx, ly + 55);

        // Mach — cor muda progressivamente
        float machPct = (float) Math.min(data.mach / 3.5, 1.0);
        glColor4f(machPct, 1f - machPct * 0.3f, 0.3f - machPct * 0.3f, flickerAlpha);
        textRenderer.renderText(String.format("MACH    %.2f", data.mach), lx, ly + 75);

        // Heading com indicador de direção
        double heading = data.yaw % 360;
        if (heading < 0) heading += 360;
        String dir = getCompassDir(heading);
        glColor4f(0f, 0.7f, 0.3f, flickerAlpha);
        textRenderer.renderText(String.format("HDG  %03.0f %s", heading, dir), lx, ly + 95);

        // Pitch e Bank
        glColor4f(0f, 0.55f, 0.25f, flickerAlpha);
        textRenderer.renderText(String.format("PIT   %+6.1f deg", data.pitch), lx, ly + 115);
        textRenderer.renderText(String.format("BNK   %+6.1f deg", data.roll), lx, ly + 135);

        // Throttle com barra visual
        double thrPct = (fbw.getThrottle() / 6000.0) * 100.0;
        glColor4f(0f, 0.55f, 0.25f, flickerAlpha);
        textRenderer.renderText(String.format("THR   %5.0f%%", thrPct), lx, ly + 155);

        // Barra de throttle inline
        glDisable(GL_TEXTURE_2D);
        float barX = lx + 160, barW = 100, barH = 10, barY2 = ly + 157;
        glColor4f(0.1f, 0.15f, 0.1f, flickerAlpha);
        glBegin(GL_QUADS);
        glVertex2f(barX, barY2); glVertex2f(barX + barW, barY2);
        glVertex2f(barX + barW, barY2 + barH); glVertex2f(barX, barY2 + barH);
        glEnd();
        glColor4f(0f, 0.8f, 0.3f, flickerAlpha);
        float fillW = barW * (float)(thrPct / 100.0);
        glBegin(GL_QUADS);
        glVertex2f(barX, barY2); glVertex2f(barX + fillW, barY2);
        glVertex2f(barX + fillW, barY2 + barH); glVertex2f(barX, barY2 + barH);
        glEnd();
        glEnable(GL_TEXTURE_2D);

        // Timestamp fake
        glColor4f(0f, 0.3f, 0.12f, flickerAlpha * 0.6f);
        int secs = (int)(crtTime) % 60;
        int mins = ((int)(crtTime) / 60) % 60;
        textRenderer.renderText(String.format("REC %02d:%02d", mins, secs), lx, ly + 178);

        // ── PAINEL DIREITO — sistemas ─────────────────────────────
        float rx = 1010 + gx, ry = 30;

        glDisable(GL_TEXTURE_2D);
        glColor4f(0f, 0.02f, 0f, 0.75f * flickerAlpha);
        glBegin(GL_QUADS);
        glVertex2f(rx - 10, ry - 10);
        glVertex2f(rx + 270, ry - 10);
        glVertex2f(rx + 270, ry + 135);
        glVertex2f(rx - 10, ry + 135);
        glEnd();

        glColor4f(0f, borderPulse, 0f, 0.7f * flickerAlpha);
        glBegin(GL_LINE_LOOP);
        glVertex2f(rx - 10, ry - 10);
        glVertex2f(rx + 270, ry - 10);
        glVertex2f(rx + 270, ry + 135);
        glVertex2f(rx - 10, ry + 135);
        glEnd();
        glEnable(GL_TEXTURE_2D);

        glColor4f(0f, 0.7f, 0.25f, flickerAlpha);
        textRenderer.renderText(">> SYSTEMS" + cursor, rx, ry);
        glColor4f(0f, 0.25f, 0f, flickerAlpha);
        textRenderer.renderText("========================", rx, ry + 16);

        // Engine temp
        double temp = fbw.getEngineTemp();
        boolean tempWarn = temp > 80;
        if (tempWarn && ((int)(crtTime * 4)) % 2 == 0)
            glColor4f(1f, 0.2f, 0.1f, flickerAlpha);
        else
            glColor4f(0f, 0.7f, 0.3f, flickerAlpha);
        String tempStatus = temp > 90 ? "CRIT" : temp > 80 ? "WARN" : "NORM";
        textRenderer.renderText(String.format("ENG  %4.0fC [%s]", temp, tempStatus), rx, ry + 35);

        // Fuel
        double fuelPct = (fbw.getFuel() / 100000.0) * 100.0;
        boolean fuelWarn = fuelPct < 20;
        if (fuelWarn && ((int)(crtTime * 3)) % 2 == 0)
            glColor4f(1f, 0.3f, 0f, flickerAlpha);
        else
            glColor4f(0f, 0.7f, 0.3f, flickerAlpha);
        textRenderer.renderText(String.format("FUEL %4.0f%%  %,.0fL", fuelPct, fbw.getFuel()), rx, ry + 55);

        // G-force com alerta visual progressivo
        double g = fbw.getGForce();
        String gWarn = g > 7 ? " !!LIMIT!!" : g > 5 ? " !CAUTION!" : g > 3 ? " *HIGH*" : "";
        if (g > 5) glColor4f(1f, 0f, 0f, flickerAlpha);
        else if (g > 3) glColor4f(1f, 0.8f, 0f, flickerAlpha);
        else glColor4f(0f, 0.7f, 0.3f, flickerAlpha);
        textRenderer.renderText(String.format("G   %+5.1f%s", g, gWarn), rx, ry + 75);

        // Posição
        glColor4f(0f, 0.45f, 0.2f, flickerAlpha);
        textRenderer.renderText(String.format("LAT  %+.0f", fbw.getPosicao().x), rx, ry + 95);
        textRenderer.renderText(String.format("LON  %+.0f", fbw.getPosicao().z), rx, ry + 115);

        // ── ALERTA DE MÍSSIL — mais agressivo ─────────────────────
        if (enemy.isMissileWarning()) {
            boolean blink = ((int)(crtTime * 6)) % 2 == 0;
            if (blink) {
                glDisable(GL_TEXTURE_2D);
                glColor4f(0.9f, 0f, 0f, 0.95f);
                glBegin(GL_QUADS);
                glVertex2f(350, 0); glVertex2f(930, 0);
                glVertex2f(930, 35); glVertex2f(350, 35);
                glEnd();
                glEnable(GL_TEXTURE_2D);
                glColor3f(1f, 1f, 1f);
                textRenderer.renderText(">>> MISSILE WARNING — [S] COUNTERMEASURE <<<", 365, 10);
            }
            // Linha de glitch extra durante alerta
            glDisable(GL_TEXTURE_2D);
            float missileGlitch = (float)(Math.random() * 720);
            glColor4f(1f, 0f, 0f, 0.15f);
            glBegin(GL_QUADS);
            glVertex2f(0, missileGlitch); glVertex2f(1280, missileGlitch);
            glVertex2f(1280, missileGlitch + 3); glVertex2f(0, missileGlitch + 3);
            glEnd();
            glEnable(GL_TEXTURE_2D);
        }

        // ── MISSÃO ATIVA ──────────────────────────────────────────
        Mission current = missionManager.currentMission();
        if (current != null && current.getState() == Mission.State.ACTIVE) {
            glColor4f(0f, 0.9f, 0.35f, flickerAlpha);
            textRenderer.renderText("> " + current.getHudStatus(), 400, 690);
        }

        // ── CLIMA ─────────────────────────────────────────────────
        String weatherLabel = weather.getHudLabel();
        if (!weatherLabel.isEmpty()) {
            glColor4f(0.8f, 0.5f, 0f, flickerAlpha);
            textRenderer.renderText("WX: " + weatherLabel, 400, 670);
        }

        // ── RODAPÉ ────────────────────────────────────────────────
        glColor4f(0f, 0.25f, 0.12f, 0.6f);
        textRenderer.renderText("E=View  F=Recon  M=Map  W/Q=Throttle  ESC=Pause", 320, 710);

        // FPS
        glColor4f(0f, 0.2f, 0.1f, 0.5f);
        textRenderer.renderText(String.format("%.0f FPS", 1.0f / lastDelta), 1200, 710);
        
        Mission mCurrent = missionManager.currentMission();
        if (mCurrent instanceof fbw.gameplay.MissionRecon recon && 
            mCurrent.getState() == Mission.State.ACTIVE) {
            
            Vector3f tgt = recon.getTarget();
            Vector3f pos = fbw.getPosicao();
            FlyByWire.FlightData fd = fbw.getLastData();
            
            float dx = tgt.x - pos.x;
            float dz = tgt.z - pos.z;
            float dist = (float)Math.sqrt(dx*dx + dz*dz);
            
            // Ângulo pro alvo vs heading atual
            double tgtBearing = Math.toDegrees(Math.atan2(dx, dz));
            if (tgtBearing < 0) tgtBearing += 360;
            double hdg = fd.yaw % 360;
            if (hdg < 0) hdg += 360;
            double diff = tgtBearing - hdg;
            if (diff > 180) diff -= 360;
            if (diff < -180) diff += 360;
            
            // Seta indicadora no centro-topo da tela
            float arrowX = 640 + (float)(diff / 180.0 * 300);  // mapeia ±180° pra ±300px
            arrowX = Math.max(200, Math.min(1080, arrowX));
            
            glDisable(GL_TEXTURE_2D);
            float tPulse = 0.7f + (float)(Math.sin(crtTime * 4) * 0.3);
            glColor4f(1f, 0.3f, 0.2f, tPulse);
            
            // Triângulo apontando pra baixo
            glBegin(GL_TRIANGLES);
            glVertex2f(arrowX, 45);
            glVertex2f(arrowX - 8, 35);
            glVertex2f(arrowX + 8, 35);
            glEnd();
            
            glEnable(GL_TEXTURE_2D);
            glColor4f(1f, 0.4f, 0.3f, tPulse);
            textRenderer.renderText(String.format("TGT %.0fkm %s", 
                dist / 1000f,
                Math.abs(diff) < 5 ? "ON NOSE" : 
                diff > 0 ? "RIGHT" : "LEFT"), arrowX - 40, 50);
        }
    }

    private String getCompassDir(double heading) {
        String[] dirs = {"N","NNE","NE","ENE","E","ESE","SE","SSE",
                         "S","SSW","SW","WSW","W","WNW","NW","NNW"};
        int idx = (int)((heading + 11.25) / 22.5) % 16;
        return dirs[idx];
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
        if (skyRenderer != null) skyRenderer.cleanup();
        if (terrainScene != null) Assimp.aiReleaseImport(terrainScene);
        if (heightmapTerrain != null) heightmapTerrain.cleanup();
        if (engineAudio != null) engineAudio.cleanup();
        audio.stopMusic();
        audio.stopSound();
        for (Model m : terrainModels) m.cleanup();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private int loadTexture(String filePath) {
        IntBuffer w = BufferUtils.createIntBuffer(1);
        IntBuffer h = BufferUtils.createIntBuffer(1);
        IntBuffer c = BufferUtils.createIntBuffer(1);

        stbi_set_flip_vertically_on_load(false);
        ByteBuffer image = stbi_load(filePath, w, h, c, 4);
        if (image == null)
            throw new RuntimeException("Falha ao carregar textura: " + filePath + " — " + stbi_failure_reason());

        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glGenerateMipmap(GL_TEXTURE_2D);
        stbi_image_free(image);
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
        org.joml.Vector3f sunDirection = new org.joml.Vector3f(0.8f, 0.4f, 0.3f).normalize();
        
        // A alta altitude, o sky shader já cuida do sol
        float camAlt = camera.getPosition().y;
        if (camAlt > 50000) {
            // Só passa sunDir, não renderiza billboard
            shader.bind();
            shader.setUniform3f("sunDir", sunDirection.x, sunDirection.y, sunDirection.z);
            shader.unbind();
            return;
        }

        // Abaixo de 50k — renderiza billboard com glow atmosférico
        org.joml.Vector3f camPos = camera.getPosition();
        org.joml.Vector3f sunPos = new org.joml.Vector3f(camPos).add(
            new org.joml.Vector3f(sunDirection).mul(900000f)
        );

        org.joml.Matrix4f viewMat = camera.getViewMatrix();
        org.joml.Vector3f camRight = new org.joml.Vector3f(viewMat.m00(), viewMat.m10(), viewMat.m20());
        org.joml.Vector3f camUp    = new org.joml.Vector3f(viewMat.m01(), viewMat.m11(), viewMat.m21());

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        glDepthMask(false);

        sunShader.bind();
        sunShader.setUniformMatrix4f("projection", camera.getProjectionMatrix());
        sunShader.setUniformMatrix4f("view", viewMat);
        sunShader.setUniform3f("center", sunPos.x, sunPos.y, sunPos.z);
        sunShader.setUniform3f("camRight", camRight.x, camRight.y, camRight.z);
        sunShader.setUniform3f("camUp", camUp.x, camUp.y, camUp.z);
        sunShader.setUniformFloat("size", 600f);
        sunShader.setUniformFloat("altitude", camAlt);

        sunBillboard.render();
        sunShader.unbind();

        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();
        shader.setUniform3f("sunDir", sunDirection.x, sunDirection.y, sunDirection.z);
        shader.unbind();
    }
    
    private void selectAndStart(int index) {
        audio.stopMusic();  // para música do menu
        missionManager.selectMission(index);
        Mission m = missionManager.currentMission();
        stats.begin(m != null ? m.getName() : "Voo Livre");
        startGameplay();
        transitionTo(Screen.EXTERNAL);
    }
    
    private void checkTerrainCollision() {
        if (fbw.isDead()) return;
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        Vector3f pos = fbw.getPosicao();
        float groundY = heightmapTerrain.getHeightAt(pos.x, pos.z);

        if (pos.y <= groundY + 5f) {
            System.out.println("COLLISION! Plane Y=" + pos.y + 
                " Ground Y=" + groundY + 
                " Pos X=" + pos.x + " Z=" + pos.z);
            fbw.kill();
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
        introTimer += delta;

        if (introTimer >= 10f || introSkipped) {
            audio.stopSound();
            introMusicStarted = false;
            currentScreen  = Screen.MAIN_MENU;
            introSkipped   = false;
            introTimer     = 0f;
            return;
        }

        setup2DLegado();
        glClearColor(0f, 0f, 0f, 1f);

        float t = introTimer;

        // ── IMAGENS SEQUENCIAIS com fade ─────────────────────────
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // 3 imagens: pilotos (0-3s), takeoff (3-6s), em voo (6-10s)
        int[] introImages = { texCrews, texTakeoff, texSR71One };
        float[] starts = { 0f, 3.3f, 6.6f };
        float imgDuration = 3.3f;
        float imgFade = 1.2f;

        for (int i = 0; i < introImages.length; i++) {
            float localT = t - starts[i];
            if (localT < 0 || localT > imgDuration) continue;

            float alpha;
            if (localT < imgFade)
                alpha = localT / imgFade;
            else if (localT > imgDuration - imgFade)
                alpha = (imgDuration - localT) / imgFade;
            else
                alpha = 1f;
            alpha *= 0.35f;

            // Ken Burns
            float zoom = 1.05f + (localT / imgDuration) * 0.05f;
            float panX = (float)(Math.sin(i * 2.3 + localT * 0.08) * 30);
            float panY = (float)(Math.cos(i * 1.7 + localT * 0.05) * 20);
            float imgW = 1280 * zoom;
            float imgH = 720 * zoom;
            float ix = (1280 - imgW) / 2 + panX;
            float iy = (720 - imgH) / 2 + panY;

            glColor4f(0.7f, 0.72f, 0.85f, alpha);
            glBindTexture(GL_TEXTURE_2D, introImages[i]);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(ix, iy);
            glTexCoord2f(1, 0); glVertex2f(ix + imgW, iy);
            glTexCoord2f(1, 1); glVertex2f(ix + imgW, iy + imgH);
            glTexCoord2f(0, 1); glVertex2f(ix, iy + imgH);
            glEnd();
            glBindTexture(GL_TEXTURE_2D, 0);
        }

        // ── GRADIENTES ───────────────────────────────────────────
        glDisable(GL_TEXTURE_2D);

        glBegin(GL_QUADS);
        glColor4f(0f, 0f, 0f, 0.7f);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glColor4f(0f, 0f, 0f, 0.1f);
        glVertex2f(1280, 200); glVertex2f(0, 200);
        glEnd();

        glBegin(GL_QUADS);
        glColor4f(0f, 0f, 0f, 0.1f);
        glVertex2f(0, 520); glVertex2f(1280, 520);
        glColor4f(0f, 0f, 0f, 0.85f);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        // ── PARTÍCULAS ───────────────────────────────────────────
        glPointSize(1.5f);
        glBegin(GL_POINTS);
        for (int i = 0; i < 40; i++) {
            float sx = (float)(Math.sin(i * 127.1) * 0.5 + 0.5) * 1280;
            float sy = ((float)(Math.cos(i * 311.7) * 0.5 + 0.5) * 720 - t * (2 + i % 6));
            sy = ((sy % 780) + 780) % 780 - 30;
            float sa = (float)(Math.sin(t * 0.8 + i * 0.5) * 0.5 + 0.5) * 0.12f;
            glColor4f(0.7f, 0.72f, 0.9f, sa);
            glVertex2f(sx, sy);
        }
        glEnd();
        glPointSize(1f);

        // ── TEXTOS CINEMÁTICOS ───────────────────────────────────
        glEnable(GL_TEXTURE_2D);

        // Frase 1: aparece em 1s
        if (t > 1f) {
            float a = Math.min((t - 1f) / 1.5f, 1f);
            if (t > 4f) a = Math.max(0, 1f - (t - 4f) / 1f);
            glColor4f(0.5f, 0.52f, 0.65f, a * 0.7f);
            textRenderer.renderText("LOCKHEED SKUNK WORKS // 1964", 440, 300, 16);
        }

        // Título: aparece em 3s
        if (t > 3f) {
            float a = Math.min((t - 3f) / 1.5f, 1f);
            // Sombra
            glColor4f(0f, 0f, 0f, a * 0.4f);
            textRenderer.renderText("SR-71", 442, 342, 64);
            // Principal
            glColor4f(1f, 1f, 1f, a);
            textRenderer.renderText("SR-71", 440, 340, 64);
        }

        // BLACKBIRD: aparece em 4s
        if (t > 4f) {
            float a = Math.min((t - 4f) / 1.5f, 1f);
            glColor4f(0.85f, 0.88f, 0.95f, a * 0.9f);
            textRenderer.renderText("BLACKBIRD", 442, 390, 32);
        }

        // Subtítulo: aparece em 5.5s
        if (t > 5.5f) {
            float a = Math.min((t - 5.5f) / 1.5f, 1f);
            glColor4f(0.55f, 0.58f, 0.7f, a * 0.7f);
            textRenderer.renderText("Classified. Untouchable. Unstoppable.", 400, 440, 16);
        }

        // Skip
        if (t > 2f) {
            float skipAlpha = 0.25f + (float)(Math.sin(t * 2) * 0.1);
            glColor4f(0.4f, 0.4f, 0.45f, skipAlpha);
            textRenderer.renderText("SPACE to skip", 570, 690, 16);
        }

        // Barras finas
        glDisable(GL_TEXTURE_2D);
        glColor4f(0.6f, 0.62f, 0.75f, 0.2f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glVertex2f(1280, 1); glVertex2f(0, 1);
        glEnd();
        glBegin(GL_QUADS);
        glVertex2f(0, 719); glVertex2f(1280, 719);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        drawCRTOverlay();
    }
    
    private void renderMainMenu() {
    	if (!audio.isMusicPlaying() && !transitioning) {
    	    audio.playMusic("/audio/thebeast.wav");
    	}
    	
        setup2DLegado();
        glClearColor(0f, 0f, 0f, 1f);

        float t = crtTime;
        menuBarY += (menuBarTargetY - menuBarY) * 0.1f;
        menuBarAlpha = Math.min(1f, menuBarAlpha + 0.02f);

        // ══════════════════════════════════════════════════════════
        // IMAGENS DE FUNDO — todas as fotos em crossfade
        // ══════════════════════════════════════════════════════════
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int[] bgTextures = {
            texPilots, texTakeoff, texSR71One, texMD11,
            texWeaver, texGilliland, texMarta, texCrews,
            texPilot23, rsoTextureId, backgroundTextureId
        };

        float cycleDuration = 5f;   // cada foto fica 5s
        float fadeDuration  = 1.5f; // fade in/out de 1.5s
        float blackGap      = 0.5f; // meio segundo de preto entre fotos
        float totalCycle    = cycleDuration + blackGap;

        int currentImg = ((int)(t / totalCycle)) % bgTextures.length;
        float cycleProgress = (t % totalCycle);

        // Alpha: fade in, segura, fade out, preto
        float imgAlpha = 0f;
        if (cycleProgress < fadeDuration) {
            imgAlpha = cycleProgress / fadeDuration;           // fade in
        } else if (cycleProgress < cycleDuration - fadeDuration) {
            imgAlpha = 1f;                                      // segura
        } else if (cycleProgress < cycleDuration) {
            imgAlpha = (cycleDuration - cycleProgress) / fadeDuration; // fade out
        } else {
            imgAlpha = 0f;                                      // gap preto
        }
        imgAlpha *= 0.3f;

        // Ken Burns — zoom lento + pan suave
        float zoom = 1.05f + (cycleProgress / cycleDuration) * 0.04f;
        float panX = (float)(Math.sin(t * 0.06 + currentImg * 1.5) * 35);
        float panY = (float)(Math.cos(t * 0.04 + currentImg * 2.1) * 20);

        if (imgAlpha > 0.001f) {
            // Calcula tamanho mantendo a imagem centralizada sem esticar demais
            float imgW = 1280 * zoom;
            float imgH = 720 * zoom;
            float imgX = (1280 - imgW) / 2 + panX;
            float imgY = (720 - imgH) / 2 + panY;

            glColor4f(0.75f, 0.78f, 0.9f, imgAlpha);
            glBindTexture(GL_TEXTURE_2D, bgTextures[currentImg]);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(imgX, imgY);
            glTexCoord2f(1, 0); glVertex2f(imgX + imgW, imgY);
            glTexCoord2f(1, 1); glVertex2f(imgX + imgW, imgY + imgH);
            glTexCoord2f(0, 1); glVertex2f(imgX, imgY + imgH);
            glEnd();
            glBindTexture(GL_TEXTURE_2D, 0);
        }

        // ══════════════════════════════════════════════════════════
        // GRADIENTES — moldura escura
        // ══════════════════════════════════════════════════════════
        glDisable(GL_TEXTURE_2D);

        // Topo
        glBegin(GL_QUADS);
        glColor4f(0f, 0f, 0f, 0.7f);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glColor4f(0f, 0f, 0f, 0.2f);
        glVertex2f(1280, 180); glVertex2f(0, 180);
        glEnd();

        // Bottom
        glBegin(GL_QUADS);
        glColor4f(0f, 0f, 0f, 0.2f);
        glVertex2f(0, 540); glVertex2f(1280, 540);
        glColor4f(0f, 0f, 0f, 0.75f);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        // Esquerda
        glBegin(GL_QUADS);
        glColor4f(0f, 0f, 0f, 0.8f);
        glVertex2f(0, 0); glVertex2f(0, 720);
        glColor4f(0f, 0f, 0f, 0.1f);
        glVertex2f(350, 720); glVertex2f(350, 0);
        glEnd();

        // Direita
        glBegin(GL_QUADS);
        glColor4f(0f, 0f, 0f, 0.0f);
        glVertex2f(1000, 0); glVertex2f(1000, 720);
        glColor4f(0f, 0f, 0f, 0.4f);
        glVertex2f(1280, 720); glVertex2f(1280, 0);
        glEnd();

        // ══════════════════════════════════════════════════════════
        // PARTÍCULAS — poeira flutuando
        // ══════════════════════════════════════════════════════════
        glPointSize(1.5f);
        glBegin(GL_POINTS);
        for (int i = 0; i < 50; i++) {
            float sx = (float)(Math.sin(i * 127.1) * 0.5 + 0.5) * 1280;
            float sy = ((float)(Math.cos(i * 311.7) * 0.5 + 0.5) * 720 - t * (3 + i % 8));
            sy = ((sy % 780) + 780) % 780 - 30;
            float sa = (float)(Math.sin(t * 0.8 + i * 0.5) * 0.5 + 0.5) * 0.2f;
            if (i % 3 == 0)
                glColor4f(0.8f, 0.82f, 1f, sa);
            else
                glColor4f(0.6f, 0.6f, 0.7f, sa * 0.6f);
            glVertex2f(sx, sy);
        }
        glEnd();
        glPointSize(1f);

        // ══════════════════════════════════════════════════════════
        // LINHA HORIZONTAL
        // ══════════════════════════════════════════════════════════
        float lineAlpha = 0.2f + (float)(Math.sin(t * 1.5) * 0.05);
        glColor4f(0.7f, 0.72f, 0.85f, lineAlpha);
        glBegin(GL_QUADS);
        glVertex2f(40, 355); glVertex2f(1240, 355);
        glVertex2f(1240, 356); glVertex2f(40, 356);
        glEnd();

        // ══════════════════════════════════════════════════════════
        // TÍTULO
        // ══════════════════════════════════════════════════════════
        glEnable(GL_TEXTURE_2D);

        glColor4f(0f, 0f, 0f, 0.4f);
        textRenderer.renderText("SR-71", 44, 288, 64);
        glColor4f(1f, 1f, 1f, 1f);
        textRenderer.renderText("SR-71", 40, 284, 64);

        glColor4f(0.9f, 0.92f, 1f, 0.95f);
        textRenderer.renderText("BLACKBIRD", 42, 332, 32);

        // Linha sob o título
        glDisable(GL_TEXTURE_2D);
        float titleLineW = Math.min(220, t * 160);
        glColor4f(0.7f, 0.72f, 0.85f, 0.6f);
        glBegin(GL_QUADS);
        glVertex2f(42, 354); glVertex2f(42 + titleLineW, 354);
        glVertex2f(42 + titleLineW, 355); glVertex2f(42, 355);
        glEnd();

        // ══════════════════════════════════════════════════════════
        // BARRA DE SELEÇÃO
        // ══════════════════════════════════════════════════════════
        float barTop = menuBarY - 6;
        float barBot = menuBarY + 30;

        glColor4f(0.5f, 0.52f, 0.7f, 0.12f * menuBarAlpha);
        glBegin(GL_QUADS);
        glVertex2f(30, barTop - 8);
        glVertex2f(320, barTop - 8);
        glVertex2f(320, barBot + 8);
        glVertex2f(30, barBot + 8);
        glEnd();

        glColor4f(0.85f, 0.88f, 1f, 1f * menuBarAlpha);
        glBegin(GL_QUADS);
        glVertex2f(34, barTop + 2);
        glVertex2f(37, barTop + 2);
        glVertex2f(37, barBot - 2);
        glVertex2f(34, barBot - 2);
        glEnd();

        // ══════════════════════════════════════════════════════════
        // OPÇÕES
        // ══════════════════════════════════════════════════════════
        glEnable(GL_TEXTURE_2D);
        String[] opcoes = {"JOGAR", "MISSOES", "OPCOES", "EXTRAS", "SAIR"};

        for (int i = 0; i < opcoes.length; i++) {
            float baseY = 385 + i * 50;
            boolean sel = (i == menuSelectedIndex);
            float proximity = 1f - Math.min(1f, Math.abs(menuBarY - baseY) / 50f);

            if (sel) {
                glColor4f(1f, 1f, 1f, 1f);
                textRenderer.renderText(opcoes[i], 50, baseY, 24);
            } else {
                float b = 0.45f + proximity * 0.2f;
                glColor4f(b, b, b + 0.05f, 0.85f);
                textRenderer.renderText(opcoes[i], 50, baseY, 24);
            }
        }

        // ══════════════════════════════════════════════════════════
        // FRASES
        // ══════════════════════════════════════════════════════════
        String[] phrases = {
            "MACH 3.2+ CRUISE SPEED",
            "OPERATIONAL CEILING: 85,000 FT",
            "FIRST FLIGHT: 22 DEC 1964",
            "TOTAL BUILT: 32 AIRCRAFT",
            "CREW: PILOT + RSO",
            "RANGE: 3,200 NAUTICAL MILES",
            "NEVER INTERCEPTED. NEVER SHOT DOWN."
        };
        int phraseIdx = ((int)(t / 4f)) % phrases.length;
        float phraseAlpha = (float)(Math.sin((t % 4f) / 4f * Math.PI)) * 0.55f;
        glColor4f(0.65f, 0.68f, 0.8f, phraseAlpha);
        textRenderer.renderText(phrases[phraseIdx], 750, 680, 16);

        // ══════════════════════════════════════════════════════════
        // CONTROLES
        // ══════════════════════════════════════════════════════════
        glColor4f(0.35f, 0.35f, 0.4f, 0.5f);
        textRenderer.renderText("NAVIGATE", 50, 660, 16);
        textRenderer.renderText("CONFIRM", 50, 678, 16);
        glColor4f(0.6f, 0.6f, 0.65f, 0.6f);
        textRenderer.renderText("UP / DOWN", 145, 660, 16);
        textRenderer.renderText("ENTER", 145, 678, 16);

        glColor4f(0.2f, 0.2f, 0.25f, 0.3f);
        textRenderer.renderText("v0.4.1", 1220, 700, 16);

        // Barras finas
        glDisable(GL_TEXTURE_2D);
        glColor4f(0.6f, 0.62f, 0.75f, 0.25f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glVertex2f(1280, 1); glVertex2f(0, 1);
        glEnd();
        glBegin(GL_QUADS);
        glVertex2f(0, 719); glVertex2f(1280, 719);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        drawCRTOverlay();
    }
    
    private void renderDebriefing() {
        setup2DLegado();
        glClearColor(0f, 0f, 0f, 1f);

        float t = crtTime;

        // ── IMAGEM DE FUNDO ──────────────────────────────────────
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int[] bgTextures = {
            texPilots, texSR71One, texCrews, texTakeoff,
            texGilliland, texMarta, texWeaver
        };
        float cycleDuration = 5f;
        float fadeDuration  = 1.5f;
        float blackGap      = 0.5f;
        float totalCycle    = cycleDuration + blackGap;
        int currentImg = ((int)(t / totalCycle)) % bgTextures.length;
        float cycleProgress = (t % totalCycle);

        float imgAlpha = 0f;
        if (cycleProgress < fadeDuration)
            imgAlpha = cycleProgress / fadeDuration;
        else if (cycleProgress < cycleDuration - fadeDuration)
            imgAlpha = 1f;
        else if (cycleProgress < cycleDuration)
            imgAlpha = (cycleDuration - cycleProgress) / fadeDuration;
        imgAlpha *= 0.15f;

        float zoom = 1.05f + (cycleProgress / cycleDuration) * 0.03f;
        float panX = (float)(Math.sin(t * 0.05 + currentImg) * 30);
        float panY = (float)(Math.cos(t * 0.04 + currentImg) * 15);

        if (imgAlpha > 0.001f) {
            float imgW = 1280 * zoom;
            float imgH = 720 * zoom;
            float ix = (1280 - imgW) / 2 + panX;
            float iy = (720 - imgH) / 2 + panY;
            glColor4f(0.7f, 0.72f, 0.85f, imgAlpha);
            glBindTexture(GL_TEXTURE_2D, bgTextures[currentImg]);
            glBegin(GL_QUADS);
            glTexCoord2f(0, 0); glVertex2f(ix, iy);
            glTexCoord2f(1, 0); glVertex2f(ix + imgW, iy);
            glTexCoord2f(1, 1); glVertex2f(ix + imgW, iy + imgH);
            glTexCoord2f(0, 1); glVertex2f(ix, iy + imgH);
            glEnd();
            glBindTexture(GL_TEXTURE_2D, 0);
        }

        // ── GRADIENTES ───────────────────────────────────────────
        glDisable(GL_TEXTURE_2D);

        glBegin(GL_QUADS);
        glColor4f(0f, 0f, 0f, 0.8f);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glColor4f(0f, 0f, 0f, 0.3f);
        glVertex2f(1280, 150); glVertex2f(0, 150);
        glEnd();

        glBegin(GL_QUADS);
        glColor4f(0f, 0f, 0f, 0.2f);
        glVertex2f(0, 560); glVertex2f(1280, 560);
        glColor4f(0f, 0f, 0f, 0.85f);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        // ── PARTÍCULAS ───────────────────────────────────────────
        glPointSize(1.5f);
        glBegin(GL_POINTS);
        for (int i = 0; i < 30; i++) {
            float sx = (float)(Math.sin(i * 127.1) * 0.5 + 0.5) * 1280;
            float sy = ((float)(Math.cos(i * 311.7) * 0.5 + 0.5) * 720 - t * (2 + i % 5));
            sy = ((sy % 780) + 780) % 780 - 30;
            float sa = (float)(Math.sin(t * 0.8 + i * 0.5) * 0.5 + 0.5) * 0.15f;
            glColor4f(0.7f, 0.72f, 0.9f, sa);
            glVertex2f(sx, sy);
        }
        glEnd();
        glPointSize(1f);

        // ── CABEÇALHO ────────────────────────────────────────────
        glEnable(GL_TEXTURE_2D);

        glColor4f(0.5f, 0.52f, 0.65f, 0.6f);
        textRenderer.renderText("DEBRIEFING", 80, 45, 16);

        boolean completed = stats.isCompleted();
        String result = completed ? "MISSION ACCOMPLISHED" : "MISSION FAILED";
        if (completed)
            glColor4f(0.4f, 1f, 0.5f, 1f);
        else
            glColor4f(1f, 0.35f, 0.35f, 1f);
        textRenderer.renderText(result, 80, 70, 48);

        // Linha sob o resultado
        glDisable(GL_TEXTURE_2D);
        float headerLineW = Math.min(600, t * 300);
        if (completed)
            glColor4f(0.3f, 0.8f, 0.4f, 0.4f);
        else
            glColor4f(0.8f, 0.2f, 0.2f, 0.4f);
        glBegin(GL_QUADS);
        glVertex2f(80, 100); glVertex2f(80 + headerLineW, 100);
        glVertex2f(80 + headerLineW, 101); glVertex2f(80, 101);
        glEnd();

        // ── DADOS DE VOO ─────────────────────────────────────────
        glEnable(GL_TEXTURE_2D);

        float lx = 100, rx = 500, y = 140;
        float step = 50;

        // Missão
        glColor4f(0.5f, 0.52f, 0.6f, 0.7f);
        textRenderer.renderText("MISSION", lx, y, 16);
        glColor4f(0.9f, 0.92f, 1f, 0.95f);
        textRenderer.renderText(stats.getMissionName() != null ? stats.getMissionName() : "—", rx, y, 24);
        y += step;

        // Tempo
        glColor4f(0.5f, 0.52f, 0.6f, 0.7f);
        textRenderer.renderText("FLIGHT TIME", lx, y, 16);
        glColor4f(0.9f, 0.92f, 1f, 0.95f);
        textRenderer.renderText(stats.getElapsedFormatted(), rx, y, 24);
        y += step;

        // Altitude média
        glColor4f(0.5f, 0.52f, 0.6f, 0.7f);
        textRenderer.renderText("AVG ALTITUDE", lx, y, 16);
        glColor4f(0.9f, 0.92f, 1f, 0.95f);
        textRenderer.renderText(String.format("%.0f ft", stats.getAvgAltitude()), rx, y, 24);
        y += step;

        // Altitude máxima
        glColor4f(0.5f, 0.52f, 0.6f, 0.7f);
        textRenderer.renderText("MAX ALTITUDE", lx, y, 16);
        glColor4f(0.9f, 0.92f, 1f, 0.95f);
        textRenderer.renderText(String.format("%.0f ft", stats.getMaxAltitude()), rx, y, 24);
        y += step;

        // Velocidade
        glColor4f(0.5f, 0.52f, 0.6f, 0.7f);
        textRenderer.renderText("MAX SPEED", lx, y, 16);
        glColor4f(0.9f, 0.92f, 1f, 0.95f);
        textRenderer.renderText(String.format("%.0f km/h  (Mach %.2f)",
            stats.getMaxSpeed() * 3.6, stats.getMaxMach()), rx, y, 24);
        y += step;

        // Contramedidas
        glColor4f(0.5f, 0.52f, 0.6f, 0.7f);
        textRenderer.renderText("COUNTERMEASURES", lx, y, 16);
        glColor4f(0.9f, 0.92f, 1f, 0.95f);
        textRenderer.renderText(String.valueOf(stats.getCountermeasures()), rx, y, 24);
        y += step + 10;

        // ── LINHA SEPARADORA ─────────────────────────────────────
        glDisable(GL_TEXTURE_2D);
        glColor4f(0.5f, 0.52f, 0.65f, 0.3f);
        glBegin(GL_QUADS);
        glVertex2f(80, y); glVertex2f(1200, y);
        glVertex2f(1200, y + 1); glVertex2f(80, y + 1);
        glEnd();
        y += 25;

        // ── AVALIAÇÃO ────────────────────────────────────────────
        glEnable(GL_TEXTURE_2D);
        glColor4f(0.5f, 0.52f, 0.6f, 0.7f);
        textRenderer.renderText("RATING", lx, y, 16);

        String rating = getRating();
        if (completed)
            glColor4f(0.5f, 1f, 0.6f, 1f);
        else
            glColor4f(1f, 0.35f, 0.35f, 1f);
        textRenderer.renderText(rating, rx, y, 32);

        // ── RODAPÉ ───────────────────────────────────────────────
        glColor4f(0.4f, 0.42f, 0.5f, 0.5f);
        textRenderer.renderText("CONTINUE", 80, 670, 16);
        glColor4f(0.6f, 0.62f, 0.7f, 0.6f);
        textRenderer.renderText("ENTER", 180, 670, 16);

        // Barras finas
        glDisable(GL_TEXTURE_2D);
        glColor4f(0.6f, 0.62f, 0.75f, 0.25f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glVertex2f(1280, 1); glVertex2f(0, 1);
        glEnd();
        glBegin(GL_QUADS);
        glVertex2f(0, 719); glVertex2f(1280, 719);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        // Livery unlock
        if (stats.isCompleted() && stats.getMaxMach() >= 3.5 && !liveryUnlocked[1]) {
            liveryUnlocked[1] = true;
            triggerRSO("LIVERY DESBLOQUEADA: WHITEBIRD!");
        }

        drawCRTOverlay();
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

        float t = crtTime;

        // ── IMAGEM DE FUNDO — estática, escura ───────────────────
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Imagem fixa com leve zoom
        float zoom = 1.05f + (float)(Math.sin(t * 0.3) * 0.02);
        float imgW = 1280 * zoom;
        float imgH = 720 * zoom;
        float ix = (1280 - imgW) / 2;
        float iy = (720 - imgH) / 2;

        // Tint vermelho escuro
        glColor4f(0.5f, 0.15f, 0.15f, 0.15f);
        glBindTexture(GL_TEXTURE_2D, texPilots);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(ix, iy);
        glTexCoord2f(1, 0); glVertex2f(ix + imgW, iy);
        glTexCoord2f(1, 1); glVertex2f(ix + imgW, iy + imgH);
        glTexCoord2f(0, 1); glVertex2f(ix, iy + imgH);
        glEnd();
        glBindTexture(GL_TEXTURE_2D, 0);

        // ── GRADIENTES ESCUROS ───────────────────────────────────
        glDisable(GL_TEXTURE_2D);

        // Escurecimento geral
        glColor4f(0f, 0f, 0f, 0.7f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        // Vinheta vermelha pulsante nas bordas
        float vPulse = 0.08f + (float)(Math.sin(t * 2) * 0.04);
        glColor4f(0.6f, 0f, 0f, vPulse);
        // Topo
        glBegin(GL_QUADS);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glColor4f(0.6f, 0f, 0f, 0f);
        glVertex2f(1280, 100); glVertex2f(0, 100);
        glEnd();
        // Bottom
        glColor4f(0.6f, 0f, 0f, 0f);
        glBegin(GL_QUADS);
        glVertex2f(0, 620); glVertex2f(1280, 620);
        glColor4f(0.6f, 0f, 0f, vPulse);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        // ── LINHA VERMELHA HORIZONTAL ────────────────────────────
        float lineAlpha = 0.3f + (float)(Math.sin(t * 1.5) * 0.1);
        glColor4f(0.8f, 0.1f, 0.1f, lineAlpha);
        glBegin(GL_QUADS);
        glVertex2f(0, 358); glVertex2f(1280, 358);
        glVertex2f(1280, 362); glVertex2f(0, 362);
        glEnd();

        // ── STATIC NOISE ─────────────────────────────────────────
        float noiseIntensity = 0.05f + (float)(Math.sin(t * 4) * 0.02);
        glBegin(GL_LINES);
        for (int i = 0; i < 20; i++) {
            float ny = (float)((Math.sin(i * 73.1 + t * 7) * 0.5 + 0.5) * 720);
            float nx = (float)((Math.cos(i * 131.7 + t * 3) * 0.5 + 0.5) * 800);
            float nw = 100 + (float)(Math.sin(i * 37.3) * 80);
            glColor4f(1f, 0.2f, 0.2f, noiseIntensity);
            glVertex2f(nx, ny);
            glVertex2f(nx + nw, ny);
        }
        glEnd();

        // ── TEXTO ────────────────────────────────────────────────
        glEnable(GL_TEXTURE_2D);

        // "AIRCRAFT LOST" — grande e vermelho
        glColor4f(0f, 0f, 0f, 0.5f);
        textRenderer.renderText("AIRCRAFT LOST", 322, 282, 64);
        glColor4f(1f, 0.2f, 0.2f, 1f);
        textRenderer.renderText("AIRCRAFT LOST", 320, 280, 64);

        // Subtítulo
        glColor4f(0.6f, 0.4f, 0.4f, 0.7f);
        textRenderer.renderText("The SR-71 has been lost over hostile territory.", 340, 340, 16);

        // Linha fina sob o texto
        glDisable(GL_TEXTURE_2D);
        glColor4f(0.6f, 0.1f, 0.1f, 0.3f);
        glBegin(GL_QUADS);
        glVertex2f(320, 360); glVertex2f(960, 360);
        glVertex2f(960, 361); glVertex2f(320, 361);
        glEnd();

        // Status info
        glEnable(GL_TEXTURE_2D);
        if (stats.getMissionName() != null) {
            glColor4f(0.5f, 0.35f, 0.35f, 0.6f);
            textRenderer.renderText("MISSION: " + stats.getMissionName(), 440, 400, 16);
        }

        // Continuar
        float contAlpha = 0.4f + (float)(Math.sin(t * 2.5) * 0.15);
        glColor4f(0.6f, 0.45f, 0.45f, contAlpha);
        textRenderer.renderText("ENTER to view debriefing", 475, 480, 16);

        // Barras finas vermelhas
        glDisable(GL_TEXTURE_2D);
        glColor4f(0.7f, 0.08f, 0.08f, 0.4f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glVertex2f(1280, 2); glVertex2f(0, 2);
        glEnd();
        glBegin(GL_QUADS);
        glVertex2f(0, 718); glVertex2f(1280, 718);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        drawCRTOverlay();
    }
    
    private void confirmMenuSelection() {
        switch (menuSelectedIndex) {
	        case 0 -> {
	            audio.stopMusic();  // para música do menu
	            stats.begin("Voo Livre");
	            startGameplay();
	            transitionTo(Screen.EXTERNAL);
	        }
	        case 1 -> {
	            // música continua na tela de missões
	            transitionTo(Screen.MISSION_SELECT);
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
            case 0 -> { paused = false; }
            case 1 -> { // Reiniciar
                paused = false;
                stopGameplay();
                startGameplay();
            }
            case 2 -> {
                paused = false;
                stopGameplay();
                stats.setCompleted(false);
                previousScreen = Screen.MAIN_MENU;
                transitionTo(Screen.MAIN_MENU);
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

        String[] opcoes = { "CONTINUAR", "REINICIAR", "MENU PRINCIPAL" };
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

        // ── PASSO 1: Renderiza cena ─────────────────────────────
        sceneBuffer.bind();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        renderExternalScene();
        sceneBuffer.unbind();

        // ── PASSO 2: Extrai APENAS pixels muito brilhantes ──────
        brightBuffer.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);
        bloomBrightShader.bind();
        bloomBrightShader.setUniform1i("scene", 0);
        bloomBrightShader.setUniformFloat("threshold", 0.99f);  // quase só o sol
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneBuffer.getTexture());
        postQuad.render();
        bloomBrightShader.unbind();
        brightBuffer.unbind();

        // ── PASSO 3: Blur horizontal ────────────────────────────
        blurBufferH.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        bloomBlurShader.bind();
        bloomBlurShader.setUniform1i("image", 0);
        bloomBlurShader.setUniform1i("horizontal", 1);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, brightBuffer.getTexture());
        postQuad.render();
        bloomBlurShader.unbind();
        blurBufferH.unbind();

        // ── PASSO 4: Blur vertical ──────────────────────────────
        blurBufferV.bind();
        glClear(GL_COLOR_BUFFER_BIT);
        bloomBlurShader.bind();
        bloomBlurShader.setUniform1i("image", 0);
        bloomBlurShader.setUniform1i("horizontal", 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, blurBufferH.getTexture());
        postQuad.render();
        bloomBlurShader.unbind();
        blurBufferV.unbind();

        // ── PASSO 5: Composite ──────────────────────────────────
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        finalPostShader.bind();
        finalPostShader.setUniform1i("scene", 0);
        finalPostShader.setUniform1i("bloomBlur", 1);
        finalPostShader.setUniformFloat("bloomStrength", 0.001f);
        finalPostShader.setUniformFloat("exposure", 1.0f);
        finalPostShader.setUniformFloat("vignetteStr", 0.10f);
        finalPostShader.setUniformFloat("time", postTime);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneBuffer.getTexture());
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, blurBufferV.getTexture());
        postQuad.render();
        finalPostShader.unbind();

        // ── Cleanup ─────────────────────────────────────────────
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
    
    public void triggerRSO(String message) {
        rsoVisible = true;
        rsoTimer   = 0f;
        rsoMessage = message;
    }
    
    private void renderRSOPortrait(float delta) {
        if (!rsoVisible) return;
        rsoTimer += delta;
        if (rsoTimer >= RSO_DURATION) { rsoVisible = false; return; }

        setup2DLegado();
        float alpha = 1f;
        if (rsoTimer < 0.3f) alpha = rsoTimer / 0.3f;
        if (rsoTimer > RSO_DURATION - 0.5f) alpha = (RSO_DURATION - rsoTimer) / 0.5f;

        float px = 20, py = 520;
        float pw = 180, ph = 160;

        // Moldura escura de cockpit
        glDisable(GL_TEXTURE_2D);
        glColor4f(0f, 0f, 0f, alpha * 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(px - 4, py - 4);
        glVertex2f(px + pw + 4, py - 4);
        glVertex2f(px + pw + 4, py + ph + 4);
        glVertex2f(px - 4, py + ph + 4);
        glEnd();

        // Borda verde militar
        glColor4f(0.2f, 0.7f, 0.3f, alpha * 0.8f);
        glLineWidth(2f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(px - 4, py - 4);
        glVertex2f(px + pw + 4, py - 4);
        glVertex2f(px + pw + 4, py + ph + 4);
        glVertex2f(px - 4, py + ph + 4);
        glEnd();
        glLineWidth(1f);

        // Foto
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1f, 1f, 1f, alpha);
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, rsoTextureId);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(px,      py);
        glTexCoord2f(1, 0); glVertex2f(px + pw, py);
        glTexCoord2f(1, 1); glVertex2f(px + pw, py + ph);
        glTexCoord2f(0, 1); glVertex2f(px,      py + ph);
        glEnd();
        glBindTexture(GL_TEXTURE_2D, 0);

        // Label RSO
        glColor4f(0.2f, 1f, 0.3f, alpha);
        textRenderer.renderText("RSO", px + 4, py + ph + 8);

        // Mensagem em texto
        glColor4f(0.9f, 0.9f, 0.9f, alpha);
        textRenderer.renderText(rsoMessage, px, py - 16);
    }
    
    private void attemptPhoto() {
        FlyByWire.FlightData data = fbw.getLastData();
        if (data == null) return;

        Vector3f pos = fbw.getPosicao();

        // Verifica se alguma missão de recon tem alvo próximo
        fbw.gameplay.Mission current = missionManager.currentMission();
        if (current instanceof fbw.gameplay.MissionRecon recon) {
            Vector3f target   = recon.getTarget();
            float    horizDist = (float) Math.sqrt(
                Math.pow(pos.x - target.x, 2) +
                Math.pow(pos.z - target.z, 2)
            );

            // Margem generosa para teste — 15.000 unidades
            boolean onTarget  = horizDist < 15000f;
            boolean altOk     = data.altitude >= recon.getMinAlt()
                             && data.altitude <= recon.getMaxAlt();

            if (onTarget && altOk) {
                photoTaken = true;
                photoPopupTimer = 0f;
                recon.capturePhoto();
                engineAudio.playGoodPass();
            } else {
                triggerRSO(onTarget ? "ALTITUDE OUT OF RANGE" : "NOT OVER TARGET");
            }
        } else {
            // Fora de missão — foto livre
            photoTaken      = true;
            photoPopupTimer = 0f;
            triggerRSO("PHOTO TAKEN");
        }

        // Flash na tela
        cameraFlashTimer = 8;
    }
    
    private void drawCRTOverlay() {
        setup2DLegado();
        glDisable(GL_TEXTURE_2D);

        // Scanlines — TODAS em um único draw call
        glColor4f(0f, 0f, 0f, 0.18f);
        glBegin(GL_LINES);
        for (int y = 0; y < 720; y += 3) {
            glVertex2f(0, y); glVertex2f(1280, y);
        }
        glEnd();

        // Linha de varredura animada
        float scanY = (crtTime * 0.25f % 1.0f) * 720f;
        glColor4f(1f, 1f, 1f, 0.04f);
        glBegin(GL_QUADS);
        glVertex2f(0,    scanY - 3);
        glVertex2f(1280, scanY - 3);
        glVertex2f(1280, scanY + 3);
        glVertex2f(0,    scanY + 3);
        glEnd();

        // Vinheta verde
        glColor4f(0f, 0.05f, 0f, 0.12f);
        glBegin(GL_QUADS);
        glVertex2f(0,0); glVertex2f(1280,0);
        glVertex2f(1280,720); glVertex2f(0,720);
        glEnd();
    }
    
    private void loadLivery(int index) {
        if (index < 0 || index >= liveryPaths.length) return;
        if (!liveryUnlocked[index]) return;

        // Limpa modelo antigo
        for (Model m : models) m.cleanup();
        models.clear();
        if (scene != null) Assimp.aiReleaseImport(scene);

        // Carrega novo
        scene = Assimp.aiImportFile(liveryPaths[index],
            Assimp.aiProcess_Triangulate | Assimp.aiProcess_FlipUVs | Assimp.aiProcess_GenNormals);
        if (scene != null && scene.mRootNode() != null) {
            models.addAll(Model.loadAllFromScene(scene));
            currentLivery = index;
            System.out.println("Livery carregada: " + liveryNames[index]);
        }
    }
    
    private void transitionTo(Screen target) {
        if (transitioning) return;  // ignora se já está em transição
        transitioning = true;
        transTimer    = 0f;
        transTarget   = target;
        transHalfDone = false;
    }
    
    private void renderVHSTransition(float progress) {
        setup2DLegado();
        glDisable(GL_TEXTURE_2D);

        // Intensidade: sobe até 0.5, depois desce
        float intensity = progress < 0.5f 
            ? progress / 0.5f          // 0 → 1
            : (1f - progress) / 0.5f;  // 1 → 0

        // ── 1. BLACKOUT gradual ───────────────────────────────────
        glColor4f(0f, 0f, 0f, intensity * 0.85f);
        glBegin(GL_QUADS);
        glVertex2f(0, 0); glVertex2f(1280, 0);
        glVertex2f(1280, 720); glVertex2f(0, 720);
        glEnd();

        // ── 2. BARRAS DE TRACKING (distorção horizontal) ──────────
        float barSpeed = crtTime * 200f;
        for (int i = 0; i < 5; i++) {
            float barY = ((barSpeed + i * 173.7f) % 800f) - 40f;
            float barH = 8f + (float)(Math.sin(crtTime * 3f + i) * 4f);
            float barAlpha = intensity * 0.6f;

            // Barra escura
            glColor4f(0f, 0f, 0f, barAlpha);
            glBegin(GL_QUADS);
            glVertex2f(0, barY);
            glVertex2f(1280, barY);
            glVertex2f(1280, barY + barH);
            glVertex2f(0, barY + barH);
            glEnd();

            // Deslocamento horizontal (glitch)
            float offset = (float)(Math.sin(crtTime * 15f + i * 7f)) * 30f * intensity;
            glColor4f(1f, 1f, 1f, barAlpha * 0.15f);
            glBegin(GL_QUADS);
            glVertex2f(offset, barY - 2);
            glVertex2f(1280 + offset, barY - 2);
            glVertex2f(1280 + offset, barY + barH + 2);
            glVertex2f(offset, barY + barH + 2);
            glEnd();
        }

        // ── 3. STATIC NOISE (linhas aleatórias) ───────────────────
        int noiseLines = (int)(60 * intensity);
        glBegin(GL_LINES);
        for (int i = 0; i < noiseLines; i++) {
            float ny = (float)(Math.random() * 720);
            float nx = (float)(Math.random() * 400);
            float nw = (float)(Math.random() * 300) + 50;
            float alpha = (float)(Math.random() * 0.3f * intensity);
            glColor4f(1f, 1f, 1f, alpha);
            glVertex2f(nx, ny);
            glVertex2f(nx + nw, ny);
        }
        glEnd();

        // ── 4. CHROMATIC SPLIT (aberração RGB na borda) ───────────
        float splitOffset = intensity * 6f;
        // Faixa vermelha deslocada
        glColor4f(1f, 0f, 0f, intensity * 0.08f);
        glBegin(GL_QUADS);
        glVertex2f(-splitOffset, 0);
        glVertex2f(1280 - splitOffset, 0);
        glVertex2f(1280 - splitOffset, 720);
        glVertex2f(-splitOffset, 720);
        glEnd();
        // Faixa cyan deslocada pro outro lado
        glColor4f(0f, 1f, 1f, intensity * 0.06f);
        glBegin(GL_QUADS);
        glVertex2f(splitOffset, 0);
        glVertex2f(1280 + splitOffset, 0);
        glVertex2f(1280 + splitOffset, 720);
        glVertex2f(splitOffset, 720);
        glEnd();

        // ── 5. FLASH BRANCO no momento da troca ───────────────────
        if (progress > 0.45f && progress < 0.55f) {
            float flashAlpha = (1f - Math.abs(progress - 0.5f) / 0.05f) * 0.3f;
            glColor4f(1f, 1f, 1f, Math.max(0, flashAlpha));
            glBegin(GL_QUADS);
            glVertex2f(0, 0); glVertex2f(1280, 0);
            glVertex2f(1280, 720); glVertex2f(0, 720);
            glEnd();
        }

        // ── 6. SCANLINES intensificadas ───────────────────────────
        glColor4f(0f, 0f, 0f, intensity * 0.3f);
        glBegin(GL_LINES);
        for (int y = 0; y < 720; y += 2) {
            glVertex2f(0, y); glVertex2f(1280, y);
        }
        glEnd();
    }
    
    private void updateHudGlitch() {
        glitchTimer -= lastDelta;
        
        if (glitchTimer <= 0) {

            glitchTimer    = 2f + (float)(Math.random() * 6f);
            glitchDuration = 0.05f + (float)(Math.random() * 0.15f);
            glitchOffsetX  = (float)(Math.random() * 8f - 4f);
            hudFlicker     = Math.random() < 0.3; 
        }
        
        if (glitchTimer > glitchDuration) {
            glitchOffsetX = 0;
            hudFlicker    = false;
        }
    }
    
    private void renderTargetMarker() {
        Mission current = missionManager.currentMission();
        if (!(current instanceof fbw.gameplay.MissionRecon recon)) return;
        if (current.getState() != Mission.State.ACTIVE) return;

        Vector3f target = recon.getTarget();
        
        // Coluna vertical brilhante no alvo
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);  // additive — brilha
        
        // Usa as matrizes da câmera 3D
        glUseProgram(0);
        glBindVertexArray(0);
        
        FloatBuffer fbProj = BufferUtils.createFloatBuffer(16);
        camera.getProjectionMatrix().get(fbProj);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glLoadMatrixf(fbProj);
        
        FloatBuffer fbView = BufferUtils.createFloatBuffer(16);
        camera.getViewMatrix().get(fbView);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glLoadMatrixf(fbView);
        
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);
        
        // Coluna pulsante
        float pulse = 0.5f + (float)(Math.sin(crtTime * 3.0) * 0.3);
        float tx = target.x, tz = target.z;
        
        // Coluna verde (vai do chão até 30000 de altura)
        glLineWidth(3f);
        glColor4f(0f, 1f, 0.3f, pulse * 0.4f);
        glBegin(GL_LINES);
        glVertex3f(tx, 0f, tz);
        glVertex3f(tx, 30000f, tz);
        glEnd();
        
        // Losango no topo
        float dSize = 500f;
        float dY = 25000f;
        glColor4f(1f, 0.3f, 0.3f, pulse * 0.6f);
        glBegin(GL_LINE_LOOP);
        glVertex3f(tx - dSize, dY, tz);
        glVertex3f(tx, dY + dSize, tz);
        glVertex3f(tx + dSize, dY, tz);
        glVertex3f(tx, dY - dSize, tz);
        glEnd();
        
        // Círculos no chão mostrando a zona de captura (8000 unidades)
        float captureRadius = 8000f;
        glColor4f(0f, 1f, 0.3f, pulse * 0.2f);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < 64; i++) {
            double a = Math.PI * 2.0 * i / 64;
            glVertex3f(tx + (float)Math.cos(a) * captureRadius, 100f, 
                       tz + (float)Math.sin(a) * captureRadius);
        }
        glEnd();
        
        // Círculo da zona hostil (25000 unidades) — vermelho
        float hostileRadius = 25000f;
        glColor4f(1f, 0.2f, 0.1f, pulse * 0.15f);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < 64; i++) {
            double a = Math.PI * 2.0 * i / 64;
            glVertex3f(tx + (float)Math.cos(a) * hostileRadius, 100f, 
                       tz + (float)Math.sin(a) * hostileRadius);
        }
        glEnd();
        
        glLineWidth(1f);
        glDepthMask(true);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }
    
    private void renderStars(float altitude) {
        if (altitude < 35000) return;

        float starAlpha = (altitude - 35000f) / 50000f;
        starAlpha = Math.min(1f, starAlpha);

        Vector3f camPos = camera.getPosition();

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);  // additive — estrelas brilham

        glUseProgram(0);
        glBindVertexArray(0);

        // Usa matrizes da câmera
        FloatBuffer fbProj = BufferUtils.createFloatBuffer(16);
        camera.getProjectionMatrix().get(fbProj);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glLoadMatrixf(fbProj);

        FloatBuffer fbView = BufferUtils.createFloatBuffer(16);
        camera.getViewMatrix().get(fbView);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glLoadMatrixf(fbView);

        glPointSize(1.5f);
        glBegin(GL_POINTS);
        for (int i = 0; i < 300; i++) {
            // Posições fixas no "dome" celeste (determinístico)
            double theta = Math.sin(i * 127.1 + 0.5) * Math.PI;
            double phi   = Math.cos(i * 311.7 + 0.3) * Math.PI * 2;
            float dist   = 80000f;

            float sx = camPos.x + (float)(Math.sin(theta) * Math.cos(phi)) * dist;
            float sy = (float)(Math.abs(Math.cos(theta))) * dist + 5000f;  // só acima do horizonte
            float sz = camPos.z + (float)(Math.sin(theta) * Math.sin(phi)) * dist;

            // Cintilação
            float twinkle = 0.5f + (float)(Math.sin(crtTime * (1.5 + (i % 7) * 0.3) + i * 0.8) * 0.5);

            // Variação de brilho por estrela
            float brightness = 0.4f + (float)(Math.sin(i * 73.1) * 0.5 + 0.5) * 0.6f;

            // Algumas estrelas maiores
            if (i % 30 == 0) {
                glEnd();
                glPointSize(2.5f);
                glBegin(GL_POINTS);
            } else if (i % 30 == 1) {
                glEnd();
                glPointSize(1.5f);
                glBegin(GL_POINTS);
            }

            // Cor: maioria branca, algumas azuladas, algumas amareladas
            float r, g, b;
            if (i % 13 == 0) {
                r = 0.7f; g = 0.8f; b = 1.0f;     // azulada
            } else if (i % 17 == 0) {
                r = 1.0f; g = 0.9f; b = 0.7f;     // amarelada
            } else {
                r = 0.95f; g = 0.95f; b = 1.0f;   // branca
            }

            glColor4f(r, g, b, starAlpha * twinkle * brightness);
            glVertex3f(sx, sy, sz);
        }
        glEnd();
        glPointSize(1f);

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST);
    }
    
    private int createWaterPlane() {
        float[] verts = {
            -0.5f, 0, -0.5f,  0,1,0,  0,0,
             0.5f, 0, -0.5f,  0,1,0,  1,0,
             0.5f, 0,  0.5f,  0,1,0,  1,1,
            -0.5f, 0, -0.5f,  0,1,0,  0,0,
             0.5f, 0,  0.5f,  0,1,0,  1,1,
            -0.5f, 0,  0.5f,  0,1,0,  0,1
        };
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);
        
        int vbo = glGenBuffers();
        FloatBuffer buf = BufferUtils.createFloatBuffer(verts.length);
        buf.put(verts).flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        
        int stride = 8 * 4; // 3 pos + 3 norm + 2 uv = 8 floats * 4 bytes
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3 * 4);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6 * 4);
        
        glBindVertexArray(0);
        return vao;
    }
    
    public static void main(String[] args) { new FlyData().run(); }
}