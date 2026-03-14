#version 330 core

out vec4 FragColor;

in vec3 Normal;
in vec3 FragPos;
in vec2 TexCoords;

// Mudamos de lightPos (ponto) para lightDir (direção do sol)
uniform vec3 lightDir; 
uniform vec3 viewPos;
uniform vec3 objectColor;
uniform vec3 lightColor;

uniform sampler2D texture1;
uniform bool useTexture; 

// NOVO: Cor do céu para a neblina
uniform vec3 skyColor; 

void main() {
    // 1. Luz Ambiente
    float ambientStrength = 0.3;
    vec3 ambient = ambientStrength * lightColor;

    // 2. Luz Difusa (Sol Direcional)
    vec3 norm = normalize(Normal);
    // Invertemos a direção do sol para apontar PARA a fonte de luz
    vec3 lightDirNorm = normalize(-lightDir); 
    float diff = max(dot(norm, lightDirNorm), 0.0);
    vec3 diffuse = diff * lightColor;

    // 3. Brilho Especular
    float specularStrength = 0.5;
    vec3 viewDir = normalize(viewPos - FragPos);
    vec3 reflectDir = reflect(-lightDirNorm, norm);
    float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32);
    vec3 specular = specularStrength * spec * lightColor;

    // Aplica Textura ou Cor
    vec3 colorToUse;
    if (useTexture) {
        colorToUse = texture(texture1, TexCoords).rgb;
    } else {
        colorToUse = objectColor;
    }

    vec3 result = (ambient + diffuse + specular) * colorToUse;

    // --- 4. NEBLINA ATMOSFÉRICA (FOG) ---
    // Calcula a distância da câmera até este pixel
    float distance = length(viewPos - FragPos);
    
    // Quão densa é a neblina
    float fogDensity = 0.00004; 
    
    // Fórmula matemática da neblina exponencial
    float fogFactor = exp(-pow((distance * fogDensity), 2.0));
    fogFactor = clamp(fogFactor, 0.0, 1.0);

    // Mistura a cor do objeto com a cor do céu baseado na distância
    FragColor = vec4(mix(skyColor, result, fogFactor), 1.0);
}