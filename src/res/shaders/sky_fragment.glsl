#version 330 core
in  vec2 TexCoord;
out vec4 FragColor;

uniform mat4 invProj;
uniform mat4 invView;
uniform vec3 sunDir;
uniform vec3 camPos;

void main() {
    // Reconstrói o view ray a partir do pixel de tela
    vec4 rayClip  = vec4(TexCoord, 1.0, 1.0);
    vec4 rayView  = invProj * rayClip;
    rayView       = vec4(rayView.xy, -1.0, 0.0);
    vec3 rayWorld = normalize((invView * rayView).xyz);

    float upDot  = rayWorld.y;
    float sunDot = max(dot(rayWorld, normalize(sunDir)), 0.0);

    // Gradiente vertical
    vec3 zenith  = vec3(0.03, 0.11, 0.70);
    vec3 mid     = vec3(0.16, 0.44, 0.86);
    vec3 horizon = vec3(0.60, 0.78, 0.96);

    float tz = clamp(upDot * 4.0, 0.0, 1.0);
    float tm = clamp(upDot * 1.5 + 0.1, 0.0, 1.0);
    vec3 sky = mix(horizon, mid, tm);
    sky      = mix(sky, zenith, tz);

    // Aquecimento solar — afeta principalmente horizonte e meio
    float sunInfluence = pow(sunDot, 2.0) * clamp(1.0 - upDot * 2.5, 0.0, 1.0);
    sky += vec3(1.0, 0.72, 0.30) * sunInfluence * 0.95;

    // Halo Mie ao redor do sol
    float mie = pow(sunDot, 5.0) * clamp(1.0 - abs(upDot) * 1.5, 0.0, 1.0);
    sky += mix(vec3(1.0, 0.60, 0.20), vec3(1.0, 0.90, 0.65), sunDot) * mie * 1.3;

    // Disco solar
    sky += vec3(1.0, 0.97, 0.88) * pow(sunDot, 180.0) * 5.0;

    // Haze no horizonte — quente do lado do sol, frio do lado oposto
    float haze     = pow(clamp(1.0 - abs(upDot), 0.0, 1.0), 7.0);
    vec3  hazeWarm = vec3(1.0, 0.82, 0.58);
    vec3  hazeCool = vec3(0.72, 0.85, 0.98);
    vec3  hazeCol  = mix(hazeCool, hazeWarm, sunDot * sunDot * 1.5);
    sky = mix(sky, hazeCol, haze * 0.65);

    // Abaixo do horizonte — terra (não deveria aparecer mas por segurança)
	if (upDot < -0.02) {
	    sky = mix(sky, vec3(0.55, 0.50, 0.42), clamp(-upDot * 5.0, 0.0, 1.0));
	}

    FragColor = vec4(sky, 1.0);
}