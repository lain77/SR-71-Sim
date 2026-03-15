#version 330 core

in  vec2 TexCoord;
out vec4 FragColor;

void main() {
    float d = length(TexCoord);
    if (d > 1.0) discard;

    float core   = smoothstep(0.18, 0.0,  d);          // disco menor e mais duro
    float halo   = smoothstep(1.0,  0.0,  d) * 0.8;   // halo mais forte
    float corona = smoothstep(1.0,  0.4,  d) * 0.4;

    vec3  sunColor = mix(vec3(1.0, 0.75, 0.3), vec3(1.0, 1.0, 0.95), core);
    float alpha    = clamp(core + halo + corona, 0.0, 1.0);

    FragColor = vec4(sunColor, alpha);
}