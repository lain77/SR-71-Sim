#version 330 core

in  vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D scene;
uniform sampler2D bloomBlur;
uniform float bloomStrength;
uniform float exposure;
uniform float vignetteStr;
uniform float time;

void main() {
    vec3 color = texture(scene, TexCoord).rgb;
    vec3 bloom = texture(bloomBlur, TexCoord).rgb;
    color += bloom * bloomStrength;

    // Sem tonemapping — a cena já é LDR
    // Sem gamma — OpenGL já está em sRGB

    // Vinheta sutil
    vec2 uv  = TexCoord - 0.5;
    float vig = 1.0 - dot(uv, uv) * vignetteStr * 2.0;
    color *= clamp(vig, 0.0, 1.0);

    FragColor = vec4(color, 1.0);
}