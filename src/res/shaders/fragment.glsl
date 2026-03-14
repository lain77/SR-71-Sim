#version 330 core

out vec4 FragColor;

in vec3 Normal;
in vec3 FragPos;
in vec2 TexCoords;

uniform vec3  lightDir;
uniform vec3  viewPos;
uniform vec3  objectColor;
uniform vec3  lightColor;
uniform vec3  skyColor;
uniform vec3  rimColor;
uniform float rimStrength;
uniform vec3  emissiveColor;
uniform float emissiveStrength;
uniform float fogDensity;
uniform vec3 sunDir;

uniform bool useTexture;
uniform sampler2D texture1;   // grama / solo

// Texturas extras do terreno (só ativas quando useTexture=true no chão)
uniform sampler2D texRock;    // rocha para encostas íngremes
uniform sampler2D texSnow;    // neve para picos
uniform bool      useTerrain; // true só para o chão

void main() {
    vec3 norm      = normalize(Normal);
    vec3 lightDirN = normalize(-lightDir);
    vec3 viewDir   = normalize(viewPos - FragPos);

    // Ambiente hemisférico
    float hemi   = dot(norm, vec3(0.0, 1.0, 0.0)) * 0.5 + 0.5;
    vec3 ambient = mix(vec3(0.04, 0.05, 0.03), lightColor * 0.25, hemi);

    // Difusa com wrap
    float NdotL  = max(dot(norm, lightDirN), 0.0);
    float wrapped = NdotL * 0.8 + 0.2;
    vec3 diffuse  = wrapped * lightColor;

    // Especular Blinn-Phong + Fresnel
    vec3  halfDir  = normalize(lightDirN + viewDir);
    float NdotH    = max(dot(norm, halfDir), 0.0);
    float spec     = pow(NdotH, 64.0);
    float fresnel  = pow(1.0 - max(dot(norm, viewDir), 0.0), 3.0);
    vec3  specular = mix(spec, spec * 1.8, fresnel) * 0.4 * lightColor;

    // Rim light
    float rimDot = 1.0 - max(dot(norm, viewDir), 0.0);
    float rim    = smoothstep(0.5, 1.0, rimDot) * rimStrength * NdotL;
    vec3  rimContrib = rim * rimColor;

    // ── COR BASE ────────────────────────────────────────────────────
    vec3 baseColor;

    if (useTerrain) {
        // Blending por altitude e inclinação
        float height = FragPos.y;

        // Inclinação: quanto a normal aponta para cima (1=plano, 0=parede vertical)
        float slope = dot(norm, vec3(0.0, 1.0, 0.0));

        // Camada 1: grama/solo (baixo e plano)
        vec3 colGrass = texture(texture1, TexCoords).rgb;

        // Camada 2: rocha (encostas íngremes OU altitude média)
        vec3 colRock  = texture(texRock, TexCoords * 0.5).rgb;

        // Camada 3: neve (picos altos)
        vec3 colSnow  = texture(texSnow, TexCoords * 0.3).rgb;

        // Blend grama → rocha pela inclinação
        float slopeBlend = smoothstep(0.7, 0.4, slope); // encosta > 60° vira rocha
        vec3 layer1 = mix(colGrass, colRock, slopeBlend);

        // Blend layer1 → rocha pela altitude média
		float altMidBlend = smoothstep(400.0,  1200.0, height);
        vec3 layer2 = mix(layer1, colRock, altMidBlend * 0.5);

        // Blend layer2 → neve nos picos (acima de 1000u)
		float snowBlend   = smoothstep(1800.0, 2800.0, height);
        // Neve só em superfícies relativamente planas (slope > 0.5)
        snowBlend *= smoothstep(0.3, 0.6, slope);
        baseColor = mix(layer2, colSnow, snowBlend);

    } else if (useTexture) {
        baseColor = texture(texture1, TexCoords).rgb;
    } else {
        baseColor = objectColor;
    }

    vec3 result = (ambient + diffuse + specular) * baseColor + rimContrib;
    result += emissiveColor * emissiveStrength;

    // Reflexo dourado no chão (sun scatter)
    if (useTerrain) {
        // Brilho quente nas superfícies que apontam para o sol
        float sunScatter = pow(max(NdotL, 0.0), 3.0) * 0.4;
        result += vec3(1.0, 0.7, 0.2) * sunScatter * baseColor;
    }

    // Neblina com scattering atmosférico
    float dist      = length(viewPos - FragPos);
    float fogFactor = exp(-pow(dist * fogDensity, 2.0));
    fogFactor       = clamp(fogFactor, 0.0, 1.0);
    float heightFactor = clamp(FragPos.y / 8000.0, 0.0, 1.0);
    fogFactor = mix(fogFactor, 1.0, heightFactor * 0.5);

    // Direção da câmera para este fragmento
    vec3 viewRay = normalize(FragPos - viewPos);

    // Mie scattering: halo apertado em volta do sol
    float sunDot  = max(dot(viewRay, sunDir), 0.0);
    float mie     = pow(sunDot, 12.0) * 0.6;
    // Rayleigh: brilho largo no horizonte em direção ao sol
    float rayleigh = pow(sunDot, 2.0) * 0.2;

    vec3 scatterColor = vec3(1.0, 0.75, 0.35) * mie
                      + vec3(0.9, 0.65, 0.25) * rayleigh;

    vec3 finalSky = skyColor + scatterColor;

    FragColor = vec4(mix(finalSky, result, fogFactor), 1.0);
}