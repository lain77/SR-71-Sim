#version 330 core

layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aTexCoords;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;
uniform vec3 viewPos;

out vec3 Normal;
out vec3 FragPos;
out vec2 TexCoords;

void main() {
    FragPos   = vec3(model * vec4(aPos, 1.0));
    Normal    = mat3(transpose(inverse(model))) * aNormal;
    TexCoords = aTexCoords;

    // Curvatura da Terra
    // Raio da Terra em ft: ~20,900,000
    // Drop = dist² / (2 * R)
	float earthRadius = 5000000.0;
    float dx = FragPos.x - viewPos.x;
    float dz = FragPos.z - viewPos.z;
    float distSq = dx * dx + dz * dz;
    float curveDrop = distSq / (2.0 * earthRadius);

    vec3 curvedPos = FragPos;
    curvedPos.y -= curveDrop;

    gl_Position = projection * view * vec4(curvedPos, 1.0);
}