#version 330 core

in  vec2 TexCoord;
out vec4 FragColor;

void main() {
    float d = length(TexCoord);

    // Descarta fora do halo
    if (d > 1.0) discard;

    // Disco branco duro no centro
    float core  = smoothstep(0.25, 0.0,  d);
    // Halo amarelado ao redor
    float halo  = smoothstep(1.0,  0.0,  d) * 0.5;
    // Coroa externa (brilho difuso)
    float corona = smoothstep(1.0, 0.3, d) * 0.25;

    vec3  sunColor = mix(vec3(1.0, 0.85, 0.4), vec3(1.0, 1.0, 0.95), core);
    float alpha    = clamp(core + halo + corona, 0.0, 1.0);

    FragColor = vec4(sunColor, alpha);
}