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

        vec3 colGrass = texture(texture1, TexCoords).rgb;
        colGrass = pow(colGrass, vec3(0.85)) * vec3(0.85, 1.15, 0.75);

        vec3 colDarkGrass = colGrass * vec3(0.7, 0.9, 0.6);

        vec3 colRock = texture(texRock, TexCoords * 0.5).rgb;
        colRock = pow(colRock, vec3(0.9)) * vec3(1.05, 0.95, 0.85);

        vec3 colDry = colRock * vec3(1.2, 1.05, 0.8);

        vec3 colSnow = texture(texSnow, TexCoords * 0.3).rgb;

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
	    if (texSample.a < 0.3) discard; // descarta pixels transparentes das folhas
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

    // Céu físico
    vec3  viewRayN    = normalize(FragPos - viewPos);
    float upDot       = dot(viewRayN, vec3(0.0, 1.0, 0.0));
    vec3  zenithColor  = vec3(0.02, 0.12, 0.82);
    vec3  horizonColor = vec3(0.38, 0.65, 0.98);
    vec3  skyGradient  = mix(horizonColor, zenithColor, clamp(upDot * 3.0, 0.0, 1.0));

    float horizonGlow = pow(1.0 - abs(upDot), 5.0) * max(dot(viewRayN, sunDir), 0.0);
    vec3  sunsetColor = vec3(1.0, 0.5, 0.15) * horizonGlow * 0.6;
    float mieStrong   = pow(max(dot(viewRayN, sunDir), 0.0), 80.0) * 1.5;
    vec3  sunGlow     = vec3(1.0, 0.95, 0.7) * mieStrong;
    vec3  finalSky    = skyGradient + sunsetColor + sunGlow;

    // Atmospheric haze no horizonte
    float hazeAmount = pow(1.0 - abs(upDot), 8.0);
    vec3  hazeColor  = mix(horizonColor, vec3(0.85, 0.92, 1.0), 0.4);
    finalSky         = mix(finalSky, hazeColor, hazeAmount * 0.7);

    // Haze em objetos distantes
    float distHaze = 1.0 - exp(-dist * fogDensity * 3.0);
    result         = mix(result, hazeColor * 0.8, distHaze * hazeAmount * 0.3);

    FragColor = vec4(mix(finalSky, result, fogFactor), 1.0);
}