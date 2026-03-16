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
uniform vec3  sunDir;

uniform bool      useTexture;
uniform sampler2D texture1;
uniform sampler2D texRock;
uniform sampler2D texSnow;
uniform bool      useTerrain;

float valueNoise(float x, float z) {
    float ix = floor(x * 0.001);
    float iz = floor(z * 0.001);
    return fract(sin(ix * 127.1 + iz * 311.7) * 43758.5453);
}

void main() {
    vec3  norm      = normalize(Normal);
    vec3  lightDirN = normalize(-lightDir);
    vec3  viewDir   = normalize(viewPos - FragPos);

    float hemi    = dot(norm, vec3(0.0, 1.0, 0.0)) * 0.5 + 0.5;
    vec3  ambient = mix(vec3(0.01, 0.01, 0.02), lightColor * 0.06, hemi);

    float NdotL   = max(dot(norm, lightDirN), 0.0);
    vec3  diffuse = NdotL * lightColor;

    vec3  halfDir  = normalize(lightDirN + viewDir);
    float NdotH    = max(dot(norm, halfDir), 0.0);
    float spec     = pow(NdotH, 64.0);
    float fresnel  = pow(1.0 - max(dot(norm, viewDir), 0.0), 3.0);
    vec3  specular = mix(spec, spec * 1.8, fresnel) * 0.4 * lightColor;

    float rimDot     = 1.0 - max(dot(norm, viewDir), 0.0);
    float rim        = smoothstep(0.5, 1.0, rimDot) * rimStrength * NdotL;
    vec3  rimContrib = rim * rimColor;

    vec3 baseColor;

    if (useTerrain) {
        float height = FragPos.y;
        float slope  = dot(norm, vec3(0.0, 1.0, 0.0));

        vec2 uv1 = TexCoords * 0.4;
        vec2 uv2 = TexCoords * 0.18;
        vec2 uv3 = TexCoords * 0.08;

        vec3 colGrass = texture(texture1, uv1).rgb;
        colGrass = pow(colGrass, vec3(0.75)) * vec3(0.78, 1.08, 0.62);

        vec3 colDarkGrass = colGrass * vec3(0.65, 0.88, 0.55);

        vec3 colRock = texture(texRock, uv2).rgb;
        colRock = pow(colRock, vec3(0.88)) * vec3(1.08, 0.96, 0.82);

        vec3 colDry = colRock * vec3(1.15, 1.02, 0.78);

        vec3 colSnow = texture(texSnow, uv3).rgb;
        colSnow = mix(colSnow, vec3(0.92, 0.95, 1.0), 0.3);

        vec3 colWater = vec3(0.12, 0.28, 0.52)
                      + vec3(0.05, 0.1, 0.15) * valueNoise(FragPos.x, FragPos.z);

        float valleyBlend = smoothstep(-60.0, 200.0, height);
        vec3  groundBase  = mix(colDarkGrass, colGrass, valleyBlend);

        float dryBlend = smoothstep(100.0, 500.0, height)
                       * smoothstep(900.0, 600.0, height);
        groundBase = mix(groundBase, colDry, dryBlend * 0.4);

        float slopeBlend = smoothstep(0.75, 0.4, slope);
        vec3  layer1     = mix(groundBase, colRock, slopeBlend);

        float altRockBlend = smoothstep(1200.0, 2400.0, height);
        vec3  layer2       = mix(layer1, colRock, altRockBlend * 0.6);

        float snowBlend = smoothstep(5000.0, 7000.0, height);
        snowBlend      *= smoothstep(0.3, 0.65, slope);
        vec3  layer3    = mix(layer2, colSnow, snowBlend);

        float waterBlend = smoothstep(-30.0, -80.0, height);
        baseColor = mix(layer3, colWater, waterBlend);

    } else if (useTexture) {
        vec4 texSample = texture(texture1, TexCoords);
        if (texSample.a < 0.3) discard;
        baseColor = texSample.rgb;
    } else {
        baseColor = objectColor;
    }

    vec3 result = (ambient + diffuse + specular) * baseColor + rimContrib;
    result += emissiveColor * emissiveStrength;

    if (useTerrain) {
        float sunScatter = pow(max(NdotL, 0.0), 3.0) * 0.3;
        result += vec3(1.0, 0.7, 0.2) * sunScatter * baseColor;
    }

    // Neblina
    float dist      = length(viewPos - FragPos);
    float fogFactor = exp(-pow(dist * fogDensity, 2.0));
    fogFactor       = clamp(fogFactor, 0.0, 1.0);
    float heightFactor = clamp(FragPos.y / 20000.0, 0.0, 1.0);
    fogFactor = mix(fogFactor, 1.0, heightFactor * 0.2);

    // Fog color combina com o skybox
    vec3  viewRayN = normalize(FragPos - viewPos);
    float upDot    = dot(viewRayN, vec3(0.0, 1.0, 0.0));
    float sunDot   = max(dot(viewRayN, normalize(sunDir)), 0.0);

    vec3 fogWarm = vec3(1.0, 0.82, 0.58);
    vec3 fogCool = vec3(0.72, 0.85, 0.98);
    vec3 fogColor = mix(fogCool, fogWarm, sunDot * sunDot * 1.5);
    fogColor      = mix(fogColor, vec3(0.60, 0.78, 0.96), 0.4);

    float distHaze = 1.0 - exp(-dist * fogDensity * 3.0);
    float hazeAmt  = pow(clamp(1.0 - abs(upDot), 0.0, 1.0), 7.0);
    result = mix(result, fogColor * 0.88, distHaze * hazeAmt * 0.3);

    FragColor = vec4(mix(fogColor, result, fogFactor), 1.0);
}