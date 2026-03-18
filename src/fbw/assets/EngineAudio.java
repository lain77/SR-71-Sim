package fbw.assets;

import org.lwjgl.openal.*;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

/**
 * Real-time engine sound using OpenAL.
 * Pitch and gain adjust dynamically based on throttle and Mach.
 */
public class EngineAudio {

    private long device;
    private long context;
    private int buffer;
    private int source;
    private boolean initialized = false;
    
    private int windBuffer, windSource;
    private int boomBuffer, boomSource;
    private int altWarnBuffer, altWarnSource;
    private int fuelWarnBuffer, fuelWarnSource;
    private int overspeedBuffer, overspeedSource;

    private boolean altWarnPlaying = false;
    private boolean fuelWarnPlaying = false;
    private boolean overspeedPlaying = false;
    private boolean hasCrossedMach1 = false;
    public String pendingRSOMessage = null;
    
    // RSO voice lines
    private int rsoUnstartBuffer, rsoUnstartSource;
    private int rsoMach3Buffer, rsoMach3Source;
    private int rsoFuelBuffer, rsoFuelSource;
    private int rsoGoodPassBuffer, rsoGoodPassSource;
    private int rsoSamBuffer, rsoSamSource;

    private boolean hasSaidMach3 = false;
    private boolean hasSaidFuelLow = false;
    
    public void init(String wavResourcePath) {
        try {
            // ── Open device and context ──────────────────────────
            device = alcOpenDevice((ByteBuffer) null);
            if (device == 0) {
                System.err.println("OpenAL: Failed to open device");
                return;
            }

            ALCCapabilities alcCaps = ALC.createCapabilities(device);
            context = alcCreateContext(device, (IntBuffer) null);
            if (context == 0) {
                System.err.println("OpenAL: Failed to create context");
                return;
            }
            alcMakeContextCurrent(context);
            AL.createCapabilities(alcCaps);

            // ── Load WAV ─────────────────────────────────────────
            buffer = alGenBuffers();
            loadWav(wavResourcePath, buffer);
            
         // Wind
            windBuffer = alGenBuffers();
            loadWav("src/audio/wind.wav", windBuffer);
            windSource = alGenSources();
            alSourcei(windSource, AL_BUFFER, windBuffer);
            alSourcei(windSource, AL_LOOPING, AL_TRUE);
            alSourcef(windSource, AL_GAIN, 0.0f);

            // Sonic boom
            boomBuffer = alGenBuffers();
            loadWav("src/audio/sonicboom.wav", boomBuffer);
            boomSource = alGenSources();
            alSourcei(boomSource, AL_BUFFER, boomBuffer);

//            // Alt warning
//            altWarnBuffer = alGenBuffers();
//            loadWav("src/audio/alt_warning.wav", altWarnBuffer);
//            altWarnSource = alGenSources();
//            alSourcei(altWarnSource, AL_BUFFER, altWarnBuffer);
//            alSourcei(altWarnSource, AL_LOOPING, AL_TRUE);
//
//            // Fuel warning
//            fuelWarnBuffer = alGenBuffers();
//            loadWav("src/audio/fuel_warning.wav", fuelWarnBuffer);
//            fuelWarnSource = alGenSources();
//            alSourcei(fuelWarnSource, AL_BUFFER, fuelWarnBuffer);
//            alSourcei(fuelWarnSource, AL_LOOPING, AL_TRUE);
//
//            // Overspeed
//            overspeedBuffer = alGenBuffers();
//            loadWav("src/audio/overspeed.wav", overspeedBuffer);
//            overspeedSource = alGenSources();
//            alSourcei(overspeedSource, AL_BUFFER, overspeedBuffer);
//            alSourcei(overspeedSource, AL_LOOPING, AL_TRUE);
            
         // RSO voice lines
            rsoUnstartBuffer = alGenBuffers();
            loadWav("src/audio/rso_1.wav", rsoUnstartBuffer);
            rsoUnstartSource = alGenSources();
            alSourcei(rsoUnstartSource, AL_BUFFER, rsoUnstartBuffer);
            alSourcef(rsoUnstartSource, AL_GAIN, 0.9f);

            rsoMach3Buffer = alGenBuffers();
            loadWav("src/audio/rso_2.wav", rsoMach3Buffer);
            rsoMach3Source = alGenSources();
            alSourcei(rsoMach3Source, AL_BUFFER, rsoMach3Buffer);
            alSourcef(rsoMach3Source, AL_GAIN, 0.9f);

            rsoFuelBuffer = alGenBuffers();
            loadWav("src/audio/rso_3.wav", rsoFuelBuffer);
            rsoFuelSource = alGenSources();
            alSourcei(rsoFuelSource, AL_BUFFER, rsoFuelBuffer);
            alSourcef(rsoFuelSource, AL_GAIN, 0.9f);

            rsoGoodPassBuffer = alGenBuffers();
            loadWav("src/audio/rso_4.wav", rsoGoodPassBuffer);
            rsoGoodPassSource = alGenSources();
            alSourcei(rsoGoodPassSource, AL_BUFFER, rsoGoodPassBuffer);
            alSourcef(rsoGoodPassSource, AL_GAIN, 0.9f);

            rsoSamBuffer = alGenBuffers();
            loadWav("src/audio/rso_5.wav", rsoSamBuffer);
            rsoSamSource = alGenSources();
            alSourcei(rsoSamSource, AL_BUFFER, rsoSamBuffer);
            alSourcef(rsoSamSource, AL_GAIN, 0.9f);

            // ── Create source ────────────────────────────────────
            source = alGenSources();
            alSourcei(source, AL_BUFFER, buffer);
            alSourcei(source, AL_LOOPING, AL_TRUE);
            alSourcef(source, AL_GAIN, 0.0f);
            alSourcef(source, AL_PITCH, 0.8f);

            initialized = true;
            System.out.println("EngineAudio initialized: " + wavResourcePath);

        } catch (Exception e) {
            System.err.println("EngineAudio init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadWav(String resourcePath, int alBuffer) throws Exception {
        InputStream is = getClass().getResourceAsStream(resourcePath);
        if (is == null) {
            // Try filesystem path
            is = new java.io.FileInputStream(resourcePath);
        }

        AudioInputStream ais = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
        AudioFormat fmt = ais.getFormat();

        // Read all bytes
        byte[] data = ais.readAllBytes();
        ais.close();

        int channels = fmt.getChannels();
        int sampleSize = fmt.getSampleSizeInBits();
        int sampleRate = (int) fmt.getSampleRate();

        int alFormat;
        if (channels == 1 && sampleSize == 16) {
            alFormat = AL_FORMAT_MONO16;
        } else if (channels == 1 && sampleSize == 8) {
            alFormat = AL_FORMAT_MONO8;
        } else if (channels == 2 && sampleSize == 16) {
            alFormat = AL_FORMAT_STEREO16;
        } else {
            throw new RuntimeException("Unsupported WAV format: " + channels + "ch " + sampleSize + "bit");
        }

        ByteBuffer alData = MemoryUtil.memAlloc(data.length);
        alData.put(data).flip();

        alBufferData(alBuffer, alFormat, alData, sampleRate);
        MemoryUtil.memFree(alData);

        System.out.println("WAV loaded: " + channels + "ch, " + sampleSize + "bit, " + sampleRate + "Hz, " + data.length + " bytes");
    }

    /**
     * Start playing the engine sound (silent initially).
     */
    public void start() {
        if (!initialized) return;
        alSourcef(source, AL_GAIN, 0.0f);
        alSourcePlay(source);
        alSourcePlay(windSource);
        hasCrossedMach1 = false;
        hasSaidMach3 = false;
        hasSaidFuelLow = false;
    }

    /**
     * Stop the engine sound.
     */
    public void stop() {
        if (!initialized) return;
        alSourceStop(source);
        alSourceStop(windSource);
        alSourceStop(altWarnSource);
        alSourceStop(fuelWarnSource);
        alSourceStop(overspeedSource);
        altWarnPlaying = false;
        fuelWarnPlaying = false;
        overspeedPlaying = false;
    }

    /**
     * Update engine sound based on flight parameters.
     * Call every frame.
     *
     * @param throttle  0-6000
     * @param mach      current Mach number
     * @param altitude  current altitude in feet
     */
    public void update(double throttle, double mach, double altitude, double fuelPct) {
        if (!initialized) return;

        // ── Pitch: higher throttle/speed = higher pitch ──────
        // Base pitch 0.7 at idle, up to 1.5 at full throttle + Mach 3+
        double thrNorm = Math.min(throttle / 6000.0, 1.0);
        double machNorm = Math.min(mach / 3.5, 1.0);

        double pitch = 0.7 + thrNorm * 0.5 + machNorm * 0.3;
        pitch = Math.max(0.5, Math.min(2.0, pitch));

        // ── Gain: louder with throttle, quieter at extreme altitude ──
        double gain = 0.15 + thrNorm * 0.65 + machNorm * 0.2;

        // At extreme altitude, sound dampens slightly (thin air)
        if (altitude > 60000) {
            double altDamp = 1.0 - ((altitude - 60000) / 25000.0) * 0.3;
            gain *= Math.max(0.7, altDamp);
        }

        gain = Math.max(0.05, Math.min(1.0, gain));

        alSourcef(source, AL_PITCH, (float) pitch);
        alSourcef(source, AL_GAIN, (float) gain);
        
     // ── Wind: pitch e volume sobem com velocidade ────────
        double windGain = Math.min(machNorm * 0.6, 0.5);
        double windPitch = 0.6 + machNorm * 0.8;
        alSourcef(windSource, AL_GAIN, (float) windGain);
        alSourcef(windSource, AL_PITCH, (float) windPitch);

        // ── Sonic boom ao cruzar Mach 1 ─────────────────────
        if (mach >= 1.0 && !hasCrossedMach1) {
            alSourcef(boomSource, AL_GAIN, 0.8f);
            alSourcePlay(boomSource);
            hasCrossedMach1 = true;
        }
        if (mach < 0.95) hasCrossedMach1 = false;

        // ── Altitude warning: abaixo de 5000 ft ─────────────
        if (altitude < 5000 && altitude > 0) {
            if (!altWarnPlaying) {
                alSourcePlay(altWarnSource);
                altWarnPlaying = true;
            }
            alSourcef(altWarnSource, AL_GAIN, 0.5f);
        } else if (altWarnPlaying) {
            alSourceStop(altWarnSource);
            altWarnPlaying = false;
        }

     // ── Fuel warning: abaixo de 20% ─────────────────────
        if (fuelPct < 20.0) {
            if (!fuelWarnPlaying) {
                alSourcePlay(fuelWarnSource);
                fuelWarnPlaying = true;
            }
        } else if (fuelWarnPlaying) {
            alSourceStop(fuelWarnSource);
            fuelWarnPlaying = false;
        }

        // ── Overspeed: acima de Mach 3.5 ou temp > 85 ──────
        if (mach > 3.5) {
            if (!overspeedPlaying) {
                alSourcePlay(overspeedSource);
                overspeedPlaying = true;
            }
        } else if (overspeedPlaying) {
            alSourceStop(overspeedSource);
            overspeedPlaying = false;
        }
        
     // ── RSO: Mach 3 (uma vez por voo) ───────────────────
        if (mach >= 3.0 && !hasSaidMach3) {
            alSourcePlay(rsoMach3Source);
            pendingRSOMessage = "MACH 3 — LOOKING GOOD";
            hasSaidMach3 = true;
        }

        // ── RSO: Fuel low ────────────────────────────────────
        if (fuelPct < 20.0 && !hasSaidFuelLow) {
            alSourcePlay(rsoFuelSource);
            pendingRSOMessage = "FUEL'S GETTING LOW, PILOT";
            hasSaidFuelLow = true;
        }

        // ── RSO: SAM (quando míssil detectado) ───────────────
        // Triggado externamente via playSAMWarning()
    }

    /**
     * Check if engine sound is currently playing.
     */
    public boolean isPlaying() {
        if (!initialized) return false;
        return alGetSourcei(source, AL_SOURCE_STATE) == AL_PLAYING;
    }

    public void playUnstart() {
        if (!initialized) return;
        alSourcePlay(rsoUnstartSource);
        pendingRSOMessage = "UNSTART! UNSTART!";
    }

    public void playGoodPass() {
        if (!initialized) return;
        alSourcePlay(rsoGoodPassSource);
        pendingRSOMessage = "GOOD PASS, GOOD PASS";
    }

    public void playSAMWarning() {
        if (!initialized) return;
        if (alGetSourcei(rsoSamSource, AL_SOURCE_STATE) != AL_PLAYING) {
            alSourcePlay(rsoSamSource);
            pendingRSOMessage = "SAM SITE, 2 O'CLOCK";
        }
    }
    
    
    
    /**
     * Clean up OpenAL resources.
     */
    public void cleanup() {
        if (!initialized) return;
        
        alSourceStop(source);
        alSourceStop(windSource);
        alSourceStop(boomSource);
        alSourceStop(altWarnSource);
        alSourceStop(fuelWarnSource);
        alSourceStop(overspeedSource);
        
        alDeleteSources(source);
        alDeleteSources(windSource);
        alDeleteSources(boomSource);
        alDeleteSources(altWarnSource);
        alDeleteSources(fuelWarnSource);
        alDeleteSources(overspeedSource);
        
        alDeleteBuffers(buffer);
        alDeleteBuffers(windBuffer);
        alDeleteBuffers(boomBuffer);
        alDeleteBuffers(altWarnBuffer);
        alDeleteBuffers(fuelWarnBuffer);
        alDeleteBuffers(overspeedBuffer);
        
        alDeleteSources(rsoUnstartSource);
        alDeleteSources(rsoMach3Source);
        alDeleteSources(rsoFuelSource);
        alDeleteSources(rsoGoodPassSource);
        alDeleteSources(rsoSamSource);

        alDeleteBuffers(rsoUnstartBuffer);
        alDeleteBuffers(rsoMach3Buffer);
        alDeleteBuffers(rsoFuelBuffer);
        alDeleteBuffers(rsoGoodPassBuffer);
        alDeleteBuffers(rsoSamBuffer);
        
        alcDestroyContext(context);
        alcCloseDevice(device);
        initialized = false;
    }
}
