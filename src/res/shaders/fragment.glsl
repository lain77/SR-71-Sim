#version 330 core

out vec4 FragColor;

in vec3 Normal;
in vec3 FragPos;

uniform vec3 lightPos = vec3(0.0, 100.0, 100.0);
uniform vec3 viewPos = vec3(0.0, 0.0, 5.0);
uniform vec3 objectColor = vec3(0.2, 0.6, 1.0);
uniform vec3 lightColor = vec3(1.0, 1.0, 1.0);

void main() {
    // normalização
    vec3 norm = normalize(Normal);
    vec3 lightDir = normalize(lightPos - FragPos);

    // difusa
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * lightColor;

    // especular simples
    vec3 viewDir = normalize(viewPos - FragPos);
    vec3 reflectDir = reflect(-lightDir, norm);
    float spec = pow(max(dot(viewDir, reflectDir), 0.0), 16);
    vec3 specular = 0.5 * spec * lightColor;

    vec3 result = (diffuse + specular) * objectColor;
    FragColor = vec4(result, 1.0);
}
