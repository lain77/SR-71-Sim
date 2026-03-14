#version 330 core

layout(location = 0) in vec2 aPos; // quad -1..1

uniform vec3  center;
uniform vec3  camRight;
uniform vec3  camUp;
uniform float size;
uniform mat4  projection;
uniform mat4  view;

out vec2 TexCoord;

void main() {
    TexCoord = aPos;
    vec3 worldPos = center
                  + camRight * aPos.x * size
                  + camUp    * aPos.y * size;
    gl_Position = projection * view * vec4(worldPos, 1.0);
}