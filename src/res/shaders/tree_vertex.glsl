#version 330 core
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aUV;

uniform vec3  treePos;
uniform vec3  camRight;
uniform float treeSize;
uniform mat4  view;
uniform mat4  projection;

out vec2 TexCoord;

void main() {
    vec3 worldPos = treePos
                  + camRight * aPos.x * treeSize
                  + vec3(0, 1, 0) * aPos.y * treeSize;
    TexCoord    = aUV;
    gl_Position = projection * view * vec4(worldPos, 1.0);
}