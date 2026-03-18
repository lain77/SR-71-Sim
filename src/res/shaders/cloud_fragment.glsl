#version 330 core

in  vec2 TexCoord;
out vec4 FragColor;

uniform vec3  sunDir;
uniform float darkness;
uniform float camAltitude;
uniform sampler2D cloudTex;

void main() {
    // Sample cloud texture (has alpha for shape)
    vec4 texSample = texture(cloudTex, TexCoord * 0.5 + 0.5);

    // Discard fully transparent pixels
    if (texSample.a < 0.02) discard;

    float alpha = texSample.a;

    // UV for lighting calculations
    vec2 uv = TexCoord;
    float topLight = uv.y * 0.5 + 0.5;

    // Sun illumination
    float sunLight = max(0.0, sunDir.y) * 0.5;
    float sideLight = max(0.0, dot(normalize(vec3(uv.x, 0.3, uv.y)), normalize(sunDir))) * 0.3;
    float shadeFactor = mix(0.45, 1.0, topLight + sunLight + sideLight);

    // Darkness on base (thicker clouds are darker underneath)
    shadeFactor = mix(shadeFactor, shadeFactor * 0.4, darkness * (1.0 - topLight));

    // Cloud color from texture brightness + lighting
    vec3 brightColor = vec3(1.0, 0.99, 0.97);
    vec3 shadowColor = vec3(0.55, 0.62, 0.75);
    vec3 color = mix(shadowColor, brightColor, shadeFactor * texSample.r);

    // Sun-side warm tint
    color = mix(color, vec3(1.0, 0.93, 0.80), sunLight * topLight * 0.4);

    // Silver lining on edges (backlit by sun)
    float edgeFade = smoothstep(0.0, 0.3, alpha) * smoothstep(0.8, 0.3, alpha);
    color += vec3(0.3, 0.28, 0.25) * edgeFade * sunLight * 2.0;

    // ── VIEW FROM ABOVE ──────────────────────────────────
    float aboveCloud = clamp((camAltitude - 15000.0) / 30000.0, 0.0, 1.0);

    // From above: whiter, brighter, more solid
    vec3 topViewColor = vec3(0.95, 0.97, 1.0);
    color = mix(color, topViewColor, aboveCloud * 0.5);

    // Boost alpha when viewed from above (solid carpet)
    alpha = mix(alpha, min(alpha * 1.8, 0.9), aboveCloud);

    // Distance haze on far clouds viewed from above
    float altHaze = aboveCloud * 0.2;
    color = mix(color, vec3(0.55, 0.65, 0.85), altHaze);

    // Soften edges more
    alpha *= smoothstep(0.02, 0.15, texSample.a);

    FragColor = vec4(color, alpha);
}
