package fbw.system;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.joml.Vector3f;

public class FlyByWire {

    private boolean running = true;
    private FlightData state;

    private Vector3f posicao;    // posição 3D do avião
    private Vector3f direcao;    // direção que ele está olhando
    
    // Controle simples (pode vir de um piloto automático ou entrada do usuário)
    private double throttle = 50;      // força para acelerar o avião
    private double controlPitch = 0;   // ajuste de pitch (°/s)
    private double controlRoll = 0;    // ajuste de roll (°/s)
    private double wind = 5;           // vento constante

    public FlyByWire() {
        // Agora tem um zero a mais no final (o Yaw)
        state = new FlightData(10000, 1000, 0, 0, 0, 0); 
        posicao = new Vector3f(0, 2000f, 0); 
        direcao = new Vector3f(0, 0, -1);
        this.throttle = state.speed;
    }

    public void start() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        Runnable loop = () -> {
            if (running) {
                update(0.1); // deltaTime em segundos
                lastData = state.copy(); // copia para o FlyData
            } else {
                scheduler.shutdown();
            }
        };

        // Atualiza o estado a cada 100ms (10Hz)
        scheduler.scheduleAtFixedRate(loop, 0, 100, TimeUnit.MILLISECONDS);
    }

	public void throttleUp(double power) {
		throttle += power;
	}
	
	public void throttleDown(double power) {
		throttle -= power;
	}
	
	//Climbs to altitude
	public void climbAltitude(double pitch) {
		if(state.altitude <= 0) {
			
		}
		this.controlPitch += pitch;
		System.out.println("Climbing to altitude");
	}
	
	public void decreaseAltitude(double pitch) {
		this.controlPitch -= pitch;
	}

    // Atualiza posição e ângulos do avião
	private void update(double dt) {
	    // Limites de segurança
	    double maxAltitude = 85000; // ~85000 ft
	    double minAltitude = 0;

	    double maxSpeed = 6000; // m/s (~Mach 3.4)
	    double minSpeed = 0;

	    // Física simplificada
	    // Arrasto proporcional ao quadrado da velocidade
	    double drag = 0.01 * state.speed * state.speed;

	    // Componente vertical da velocidade (subida/descida) baseado no pitch
	    double lift = state.speed * Math.sin(Math.toRadians(state.pitch));

	    // Atualiza estado
	    state.speed += (throttle - drag) * dt;
	    state.altitude += lift * dt;
	    state.pitch += controlPitch * dt;
	    state.roll += controlRoll * dt;

	    // Aplica limites para não sair do realismo
	    if (state.speed > maxSpeed) state.speed = maxSpeed;
	    if (state.speed < minSpeed) state.speed = minSpeed;

	    if (state.altitude > maxAltitude) state.altitude = maxAltitude;
	    if (state.altitude < minAltitude) state.altitude = minAltitude;

	    if (state.pitch > 30) state.pitch = 30;
	    if (state.pitch < -30) state.pitch = -30;
	    if (state.roll > 60) state.roll = 60;
	    if (state.roll < -60) state.roll = -60;


        double turnRate = state.roll * 0.1; 
        state.yaw -= turnRate * dt;

        // --- VETOR DE DIREÇÃO CORRETO (Usando Pitch e Yaw) ---
        float dx = (float) (Math.cos(Math.toRadians(state.pitch)) * Math.sin(Math.toRadians(state.yaw)));
        float dy = (float) Math.sin(Math.toRadians(state.pitch));
        // O OpenGL considera a "frente" como negativo no eixo Z, por isso o sinal de menos
        float dz = (float) (-Math.cos(Math.toRadians(state.pitch)) * Math.cos(Math.toRadians(state.yaw)));

        direcao.set(dx, dy, dz).normalize();

        Vector3f movimento = new Vector3f(direcao).mul((float) (state.speed * dt));
        posicao.add(movimento);
	}


    private volatile FlightData lastData;

    public FlightData getLastData() {
        return lastData;
    }

    public void stop() {
        running = false;
    }

    // Classe para armazenar dados de voo
    public static class FlightData {
        public double altitude;
        public double speed;
        public double pitch;
        public double roll;
        public double mach;
        public double yaw; // NOVO: Direção do nariz (bússola)

        public FlightData(double alt, double spd, double pt, double rl, double mc, double yw) {
            altitude = alt; speed = spd; pitch = pt; roll = rl; mach = mc; yaw = yw;
        }

        public FlightData copy() {
            return new FlightData(altitude, speed, pitch, roll, mach, yaw);
        }

        // Getters
        public double getAltitude() { return altitude; }
        public double getSpeed() { return speed; }
        public double getPitch() { return pitch; }
        public double getRoll() { return roll; }
        public double getMach() { return mach; }
        public double getYaw() { return yaw; } // NOVO
    }

	public double getThrottle() {
		return throttle;
	}

	public void setThrottle(double throttle) {
		this.throttle = throttle;
	}
    
	private boolean countermeasurePressed = false;

	public void activateCountermeasure() {
	    this.countermeasurePressed = true;
	}

	public void resetCountermeasure() {
	    this.countermeasurePressed = false;
	}

	public boolean isCountermeasurePressed() {
	    return countermeasurePressed;
	}
	
	public Vector3f getPosicao() {
	    return posicao;
	}

	public Vector3f getDirecao() {
	    return direcao;
	}

	public void setPosicao(Vector3f pos) {
	    if (pos == null) return;

	    if (this.posicao == null) this.posicao = new Vector3f();

	    this.posicao.set(pos);
	}


	public void setDirecao(Vector3f direcao) {
		this.direcao = direcao;
	}
	
	
}
