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
uniform bool      useSatellite;

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
    float height = FragPos.y;

    if (useSatellite) {
        // ── TERRENO COM TEXTURA DE SATÉLITE ──────────────────
        baseColor = texture(texture1, TexCoords).rgb;

        // Oceano: onde altura é baixa, escurece pra água
        if (height < 10.0) {
            vec3 oceanDeep = vec3(0.02, 0.06, 0.15);
            vec3 oceanShallow = vec3(0.05, 0.12, 0.28);
            vec3 oceanColor = mix(oceanShallow, oceanDeep, smoothstep(-10.0, -30.0, height));
            float waterMix = smoothstep(10.0, -20.0, height);
            baseColor = mix(baseColor, oceanColor, waterMix * 0.8);
        }

    } else if (useTerrain) {
        // ── TERRENO PROCEDURAL ───────────────────────────────
        float slope = dot(norm, vec3(0.0, 1.0, 0.0));

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

        float valleyBlend = smoothstep(0.0, 1000.0, height);
        vec3  groundBase  = mix(colDarkGrass, colGrass, valleyBlend);

        float dryBlend = smoothstep(500.0, 3000.0, height)
                       * smoothstep(6000.0, 4000.0, height);
        groundBase = mix(groundBase, colDry, dryBlend * 0.4);

        float slopeBlend = smoothstep(0.75, 0.4, slope);
        vec3  layer1     = mix(groundBase, colRock, slopeBlend);

        float altRockBlend = smoothstep(6000.0, 12000.0, height);
        vec3  layer2       = mix(layer1, colRock, altRockBlend * 0.6);

        float snowBlend = smoothstep(14000.0, 17000.0, height);
        snowBlend      *= smoothstep(0.3, 0.65, slope);
        vec3  layer3    = mix(layer2, colSnow, snowBlend);

        float waterBlend = smoothstep(30.0, -30.0, height);
        vec3 colWaterDeep = vec3(0.02, 0.05, 0.12);
        vec3 colWaterShallow = vec3(0.06, 0.15, 0.30);
        vec3 waterColor = mix(colWaterShallow, colWaterDeep, smoothstep(-30.0, -150.0, height));
        float skyReflect = pow(max(dot(norm, vec3(0,1,0)), 0.0), 2.0) * 0.3;
        waterColor += vec3(0.1, 0.15, 0.25) * skyReflect;

        baseColor = mix(layer3, waterColor, waterBlend);

    } else if (useTexture) {
        vec4 texSample = texture(texture1, TexCoords);
        if (texSample.a < 0.3) discard;
        baseColor = texSample.rgb;

    } else {
        baseColor = objectColor;
    }

    // ── ILUMINAÇÃO ───────────────────────────────────────────
    vec3 result = (ambient + diffuse + specular) * baseColor + rimContrib;
    result += emissiveColor * emissiveStrength;

    if (useTerrain || useSatellite) {
        float sunScatter = pow(max(NdotL, 0.0), 3.0) * 0.3;
        result += vec3(1.0, 0.7, 0.2) * sunScatter * baseColor;
    }

    // ── NEBLINA ATMOSFÉRICA ──────────────────────────────────
    float dist = length(viewPos - FragPos);

    float camAlt = viewPos.y;
    float altMix = clamp(camAlt / 85000.0, 0.0, 1.0);

    float fogFactor = exp(-pow(dist * fogDensity, 2.0));
    fogFactor = clamp(fogFactor, 0.0, 1.0);

    vec3 viewRayN = normalize(FragPos - viewPos);
    float upDot   = dot(viewRayN, vec3(0.0, 1.0, 0.0));
    float sunDot  = max(dot(viewRayN, normalize(sunDir)), 0.0);

    vec3 fogWarm = vec3(0.85, 0.75, 0.60);
    vec3 fogCool = vec3(0.55, 0.70, 0.90);
    vec3 fogColor = mix(fogCool, fogWarm, sunDot * sunDot);

    // ── ATMOSFERA VISTA DE CIMA ──────────────────────────────
    vec3 atmosScatter = vec3(0.52, 0.65, 0.85);
    float atmosDist = 1.0 - exp(-dist * 0.000006);
    atmosDist = clamp(atmosDist, 0.0, 1.0);

    float scatterStrength = pow(altMix, 1.5) * 0.92;

    result = mix(result, atmosScatter, atmosDist * scatterStrength);

    float horizonGlow = pow(clamp(1.0 - abs(upDot), 0.0, 1.0), 2.5);
    vec3 horizonWhite = vec3(0.75, 0.82, 0.95);
    result = mix(result, horizonWhite, horizonGlow * scatterStrength * 0.7);

    result = mix(fogColor, result, fogFactor);

    FragColor = vec4(result, 1.0);
}
