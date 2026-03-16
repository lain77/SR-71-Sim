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
    vec3 hdr   = texture(scene,     TexCoord).rgb;
    vec3 bloom = texture(bloomBlur, TexCoord).rgb;
    hdr += bloom * bloomStrength;

    // Reinhard tone mapping
    vec3 mapped = hdr * exposure / (hdr * exposure + vec3(1.0));

    // Gamma
    mapped = pow(mapped, vec3(1.0 / 2.2));

    // Vinheta
    vec2  uv   = TexCoord - 0.5;
    float vig  = 1.0 - dot(uv, uv) * vignetteStr * 2.0;
    mapped    *= clamp(vig, 0.0, 1.0);

    // Scanlines CRT
//    float scanline = sin(TexCoord.y * 720.0 * 3.14159) * 0.04;
//    mapped -= scanline;

    // Linha de varredura passando
//    float scanPos  = mod(time * 0.3, 1.0);
//    float scanDist = abs(TexCoord.y - scanPos);
//    float scanGlow = exp(-scanDist * 80.0) * 0.06;
//    mapped += vec3(scanGlow);

    FragColor = vec4(mapped, 1.0);
}