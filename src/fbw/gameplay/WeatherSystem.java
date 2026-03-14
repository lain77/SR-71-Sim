package fbw.gameplay;

import java.util.Random;

public class WeatherSystem {

    public enum Intensity { CALM, LIGHT, MODERATE, SEVERE }

    private Intensity intensity = Intensity.CALM;
    private float     elapsed   = 0;
    private float     nextShift = 30f; // segundos até mudar o clima
    private final Random rng    = new Random();

    // Valores de turbulência atual
    private float pitchJolt  = 0;
    private float rollJolt   = 0;
    private float joltTimer  = 0;

    public void update(float delta, fbw.system.FlyByWire fbw) {
        elapsed += delta;

        // Muda a intensidade periodicamente
        if (elapsed >= nextShift) {
            elapsed   = 0;
            nextShift = 20f + rng.nextFloat() * 40f;
            shiftWeather();
        }

        // Aplica rajadas de turbulência
        if (intensity == Intensity.CALM) return;

        joltTimer -= delta;
        if (joltTimer <= 0) {
            scheduleNextJolt();
        }

        if (joltTimer > 0 && joltTimer < 0.3f) {
            // Momento do jolt: aplica força no pitch e roll
            fbw.applyTurbulence(pitchJolt * delta, rollJolt * delta);
        }
    }

    private void shiftWeather() {
        float r = rng.nextFloat();
        if      (r < 0.4f) intensity = Intensity.CALM;
        else if (r < 0.7f) intensity = Intensity.LIGHT;
        else if (r < 0.9f) intensity = Intensity.MODERATE;
        else               intensity = Intensity.SEVERE;

        System.out.println("[WEATHER] " + intensity);
    }

    private void scheduleNextJolt() {
        float interval = switch (intensity) {
            case CALM     -> 999f;
            case LIGHT    -> 4f  + rng.nextFloat() * 4f;
            case MODERATE -> 2f  + rng.nextFloat() * 2f;
            case SEVERE   -> 0.5f + rng.nextFloat() * 1f;
        };
        float strength = switch (intensity) {
            case CALM     -> 0;
            case LIGHT    -> 0.3f;
            case MODERATE -> 0.8f;
            case SEVERE   -> 1.8f;
        };

        joltTimer = interval;
        pitchJolt = (rng.nextFloat() - 0.5f) * 2f * strength;
        rollJolt  = (rng.nextFloat() - 0.5f) * 2f * strength;
    }

    public Intensity getIntensity() { return intensity; }

    public String getHudLabel() {
        return switch (intensity) {
            case CALM     -> "";
            case LIGHT    -> "TURBULENCIA LEVE";
            case MODERATE -> "TURBULENCIA MODERADA";
            case SEVERE   -> "TURBULENCIA SEVERA!";
        };
    }
}