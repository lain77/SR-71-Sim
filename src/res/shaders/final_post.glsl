#version 330 core
in  vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D scene;
uniform sampler2D bloomBlur;
uniform float     bloomStrength;
uniform float     exposure;

// Filmic tone mapping — preserva saturação e contraste
vec3 filmic(vec3 x) {
    x = max(vec3(0.0), x - 0.004);
    return (x * (6.2 * x + 0.5)) / (x * (6.2 * x + 1.7) + 0.06);
}

void main() {
    vec3 hdr   = texture(scene,     TexCoord).rgb;
    vec3 bloom = texture(bloomBlur, TexCoord).rgb;
    hdr += bloom * bloomStrength;

    // Reinhard simples — não desatura
    vec3 mapped = hdr * exposure / (hdr * exposure + vec3(1.0));
    
    // Gamma
    mapped = pow(mapped, vec3(1.0 / 2.2));
    
    FragColor = vec4(mapped, 1.0);
}