#version 330 core

in  vec2 TexCoord;
out vec4 FragColor;

uniform vec3  sunDir;      // direção DO SOL (para sombreamento)
uniform float darkness;    // 0 = topo iluminado, 1 = base escura

void main() {
    float d = length(TexCoord);
    if (d > 1.0) discard;

    // Borda suave — coração da esfera mais opaco
    float alpha = smoothstep(1.0, 0.2, d) * 0.55;

    // Parte de cima da esfera mais clara (iluminada pelo sol)
    // TexCoord.y vai de -1 (baixo) a 1 (cima)
    float lightFactor = TexCoord.y * 0.5 + 0.5; // 0..1
    float shade = mix(0.72, 1.0, lightFactor);   // base acinzentada, topo branco

    // Base das nuvens mais escura quando há muita camada acima
    shade = mix(shade, shade * 0.6, darkness * (1.0 - lightFactor));

    vec3 color = vec3(shade);
    // Tinge levemente de amarelo nas bordas iluminadas
    color = mix(color, vec3(1.0, 0.97, 0.88), lightFactor * 0.15);

    FragColor = vec4(color, alpha);
}