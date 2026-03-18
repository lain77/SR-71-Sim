#version 330 core
in  vec2 TexCoord;
out vec4 FragColor;

uniform mat4 invProj;
uniform mat4 invView;
uniform vec3 sunDir;
uniform vec3 camPos;
uniform float altitude;

void main() {
    vec4 rayClip  = vec4(TexCoord, 1.0, 1.0);
    vec4 rayView  = invProj * rayClip;
    rayView       = vec4(rayView.xy, -1.0, 0.0);
    vec3 rayWorld = normalize((invView * rayView).xyz);

    float upDot  = rayWorld.y;
    float sunDot = max(dot(rayWorld, normalize(sunDir)), 0.0);

    float altFactor = clamp(altitude / 85000.0, 0.0, 1.0);
    float darkening = pow(altFactor, 1.3);
    float lowAltOnly = 1.0 - darkening;

    // ── Cores do céu ─────────────────────────────────────
    vec3 zenithLow   = vec3(0.15, 0.30, 0.80);
    vec3 midLow      = vec3(0.32, 0.52, 0.88);
    vec3 horizonLow  = vec3(0.70, 0.82, 0.96);

    vec3 zenithHigh  = vec3(0.0, 0.0, 0.0);
    vec3 midHigh     = vec3(0.0, 0.01, 0.04);
    vec3 horizonHigh = vec3(0.01, 0.03, 0.10);

    vec3 zenith  = mix(zenithLow,  zenithHigh,  darkening);
    vec3 mid     = mix(midLow,     midHigh,     darkening);
    vec3 horizon = mix(horizonLow, horizonHigh, darkening);

    float tz = clamp(upDot * 4.0, 0.0, 1.0);
    float tm = clamp(upDot * 1.5 + 0.1, 0.0, 1.0);
    vec3 sky = mix(horizon, mid, tm);
    sky      = mix(sky, zenith, tz);

    // ── Sol no céu — ponto pequeno ───────────────────────
    sky += vec3(1.0, 0.98, 0.93) * pow(sunDot, 4000.0) * 2.0;

    // ── Aquecimento solar — baixa altitude ───────────────
    float sunInfluence = pow(sunDot, 3.0) * clamp(1.0 - upDot * 2.5, 0.0, 1.0);
    sky += vec3(1.0, 0.85, 0.55) * sunInfluence * 0.35 * lowAltOnly;

    // ── Horizonte esbranquiçado a baixa altitude ─────────
    float horizonHaze = pow(clamp(1.0 - abs(upDot), 0.0, 1.0), 5.0);
    float sunHorizon = pow(sunDot, 8.0) * horizonHaze;
	sky += vec3(0.8, 0.55, 0.25) * sunHorizon * 0.15;
    sky = mix(sky, vec3(0.75, 0.82, 0.92), horizonHaze * 0.4 * lowAltOnly);

    // ── Banda de atmosfera — fina no horizonte ───────────
    if (altFactor > 0.2) {
        float bandStrength = (altFactor - 0.2) / 0.8;
        float bandArea = pow(clamp(1.0 - abs(upDot + 0.02), 0.0, 1.0), 20.0);
        sky += vec3(0.20, 0.40, 0.90) * bandArea * bandStrength * 1.0;

        float glowArea = pow(clamp(-upDot - 0.01, 0.0, 0.1) / 0.1, 2.0);
        sky += vec3(0.15, 0.30, 0.65) * glowArea * bandStrength * 0.5;
    }

    // ── Abaixo do horizonte ──────────────────────────────
    if (upDot < -0.02) {
        vec3 groundLow  = vec3(0.25, 0.28, 0.35);
        vec3 groundHigh = vec3(0.03, 0.06, 0.18);
        vec3 groundColor = mix(groundLow, groundHigh, darkening);
        sky = mix(sky, groundColor, clamp(-upDot * 6.0, 0.0, 1.0));
    }

    FragColor = vec4(sky, 1.0);
}