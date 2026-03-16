#version 330 core
in  vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D scene;
uniform float     time;
uniform vec2      turbineScreenPos;
uniform float     hazeStrength;

void main() {
    FragColor = texture(scene, TexCoord);
}