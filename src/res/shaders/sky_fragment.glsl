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

    vec3 zenithLow   = vec3(0.03, 0.11, 0.70);
    vec3 midLow      = vec3(0.16, 0.44, 0.86);
    vec3 horizonLow  = vec3(0.60, 0.78, 0.96);

    vec3 zenithHigh  = vec3(0.0, 0.0, 0.0);
    vec3 midHigh     = vec3(0.0, 0.01, 0.04);
    vec3 horizonHigh = vec3(0.02, 0.06, 0.15);

    vec3 zenith  = mix(zenithLow,  zenithHigh,  darkening);
    vec3 mid     = mix(midLow,     midHigh,     darkening);
    vec3 horizon = mix(horizonLow, horizonHigh, darkening);

    float tz = clamp(upDot * 4.0, 0.0, 1.0);
    float tm = clamp(upDot * 1.5 + 0.1, 0.0, 1.0);
    vec3 sky = mix(horizon, mid, tm);
    sky      = mix(sky, zenith, tz);

    float sunFade = 1.0 - darkening * 0.7;
    float sunInfluence = pow(sunDot, 2.0) * clamp(1.0 - upDot * 2.5, 0.0, 1.0);
    sky += vec3(1.0, 0.72, 0.30) * sunInfluence * 0.95 * sunFade;

    float mie = pow(sunDot, 5.0) * clamp(1.0 - abs(upDot) * 1.5, 0.0, 1.0);
    sky += mix(vec3(1.0, 0.60, 0.20), vec3(1.0, 0.90, 0.65), sunDot) * mie * 1.3 * sunFade;

    sky += vec3(1.0, 0.97, 0.88) * pow(sunDot, 180.0) * 5.0;

    float haze     = pow(clamp(1.0 - abs(upDot), 0.0, 1.0), 7.0);
    vec3  hazeWarm = vec3(1.0, 0.82, 0.58);
    vec3  hazeCool = vec3(0.72, 0.85, 0.98);
    vec3  hazeCol  = mix(hazeCool, hazeWarm, sunDot * sunDot * 1.5);
    sky = mix(sky, hazeCol, haze * 0.65 * (1.0 - darkening * 1.5));

		// ── Banda de atmosfera — fina e brilhante no horizonte ──
	if (altFactor > 0.2) {
	    float bandStrength = (altFactor - 0.2) / 0.8;
	
	    // Banda fina e intensa (só bem perto do horizonte)
	    float bandArea = pow(clamp(1.0 - abs(upDot + 0.02), 0.0, 1.0), 16.0);
	    vec3 atmColor = mix(vec3(0.4, 0.65, 1.0), vec3(0.8, 0.9, 1.0), bandArea);
	    sky += atmColor * bandArea * bandStrength * 1.5;
	
	    // Glow sutil logo abaixo da banda (não sobe pro céu)
	    float glowArea = pow(clamp(-upDot - 0.01, 0.0, 0.15) / 0.15, 2.0);
	    sky += vec3(0.15, 0.28, 0.6) * glowArea * bandStrength * 0.5;
	}
	
	// ── Abaixo do horizonte — azul atmosférico ──────────────
	if (upDot < -0.02) {
	    vec3 groundLow  = vec3(0.35, 0.38, 0.42);
	    vec3 groundHigh = vec3(0.06, 0.12, 0.30);
	    vec3 groundColor = mix(groundLow, groundHigh, darkening);
	
	    vec3 atmosBlue = vec3(0.12, 0.25, 0.55);
	    float downHaze = clamp(darkening * 0.6, 0.0, 0.7);
	    groundColor = mix(groundColor, atmosBlue, downHaze);
	
	    sky = mix(sky, groundColor, clamp(-upDot * 5.0, 0.0, 1.0));
	}

    FragColor = vec4(sky, 1.0);
}