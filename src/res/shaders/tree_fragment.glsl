#version 330 core
in  vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D treeTex;
uniform vec3      sunDir;
uniform float     treeAlpha;

void main() {
    vec4 col = texture(treeTex, TexCoord);
    if (col.a < 0.15) discard;

    // Sombreamento simples pela direção do sol
    float light = max(0.4, dot(vec3(0,1,0), normalize(-sunDir)));
    col.rgb *= light;
    col.a   *= treeAlpha;

    FragColor = col;
}