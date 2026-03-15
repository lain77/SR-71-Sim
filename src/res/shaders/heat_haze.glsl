#version 330 core
in  vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D scene;
uniform float     time;
uniform vec2      turbineScreenPos; // posição UV das turbinas (0..1)
uniform float     hazeStrength;     // ex: 0.003

void main() {
    vec2 uv = TexCoord;

    // Distância da posição das turbinas
    float dist = length(uv - turbineScreenPos);
    float falloff = 1.0 - smoothstep(0.0, 0.15, dist);

    // Ruído ondulante com tempo
    float noise = sin(uv.y * 80.0 + time * 6.0) * cos(uv.x * 40.0 + time * 4.0);

    // Desloca o UV
    vec2 distorted = uv + vec2(noise * hazeStrength * falloff, 0.0);

    FragColor = texture(scene, distorted);
}