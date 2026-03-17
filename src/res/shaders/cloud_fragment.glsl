#version 330 core

in  vec2 TexCoord;
out vec4 FragColor;

uniform vec3  sunDir;
uniform float darkness;
uniform float camAltitude;

void main() {
    vec2  uv = TexCoord;
    float d  = length(uv);
    if (d > 1.0) discard;

    float core  = smoothstep(1.0, 0.0, d);
    float alpha = pow(core, 1.4) * 0.75;

    float topLight = uv.y * 0.5 + 0.5;

    float sunLight  = max(0.0, sunDir.y) * 0.4;
    float shadeFactor = mix(0.55, 1.0, topLight + sunLight);
    shadeFactor = mix(shadeFactor, shadeFactor * 0.5, darkness * (1.0 - topLight));

    vec3 brightColor = vec3(1.0, 0.99, 0.97);
    vec3 shadowColor = vec3(0.65, 0.72, 0.82);
    vec3 color = mix(shadowColor, brightColor, shadeFactor);

    color = mix(color, vec3(1.0, 0.95, 0.82), sunLight * topLight * 0.3);

    // ── VISTAS DE CIMA — nuvens ficam brancas brilhantes ──
    float aboveCloud = clamp((camAltitude - 15000.0) / 30000.0, 0.0, 1.0);

    // De cima: mais brancas, mais opacas, mais brilhantes
    vec3 topViewColor = vec3(0.92, 0.95, 1.0);
    color = mix(color, topViewColor, aboveCloud * 0.6);

    // Boost de alpha — nuvens mais sólidas vistas de cima (tapete branco)
    alpha = mix(alpha, min(alpha * 1.5, 0.85), aboveCloud);

    // Haze azulado nas nuvens distantes vistas de cima
    float altHaze = aboveCloud * 0.3;
    color = mix(color, vec3(0.5, 0.62, 0.82), altHaze);

    FragColor = vec4(color, alpha);
}