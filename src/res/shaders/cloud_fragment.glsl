#version 330 core

in  vec2 TexCoord;
out vec4 FragColor;

uniform vec3  sunDir;
uniform float darkness;

void main() {
    vec2  uv = TexCoord;
    float d  = length(uv);
    if (d > 1.0) discard;

    // Borda muito mais suave — núcleo opaco, bordas dissipam
    float core  = smoothstep(1.0, 0.0, d);
    float alpha = pow(core, 1.4) * 0.75;

    // Gradiente vertical — topo mais branco, base mais cinza
    float topLight = uv.y * 0.5 + 0.5; // 0=baixo, 1=cima
    
    // Iluminação pelo sol — lado voltado para o sol mais brilhante
    float sunLight  = max(0.0, sunDir.y) * 0.4;
    float shadeFactor = mix(0.55, 1.0, topLight + sunLight);
    
    // Escuridão da base (nuvens mais espessas ficam mais escuras embaixo)
    shadeFactor = mix(shadeFactor, shadeFactor * 0.5, darkness * (1.0 - topLight));

    // Cor da nuvem — branco puro no topo iluminado, cinza azulado na base
    vec3 brightColor = vec3(1.0, 0.99, 0.97);  // branco levemente quente
    vec3 shadowColor = vec3(0.65, 0.72, 0.82); // cinza azulado nas sombras
    vec3 color = mix(shadowColor, brightColor, shadeFactor);

    // Tinge levemente de amarelo nas bordas iluminadas pelo sol
    color = mix(color, vec3(1.0, 0.95, 0.82), sunLight * topLight * 0.3);

    FragColor = vec4(color, alpha);
}