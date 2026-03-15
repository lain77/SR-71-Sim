#version 330 core
in  vec2 TexCoord;
out vec4 FragColor;
uniform sampler2D image;
uniform bool      horizontal;

const float weight[5] = float[](0.2270270, 0.1945946, 0.1216216, 0.0540540, 0.0162162);

void main() {
    vec2 texOffset = 1.0 / textureSize(image, 0);
    vec3 result    = texture(image, TexCoord).rgb * weight[0];

    if (horizontal) {
        for (int i = 1; i < 5; i++) {
            result += texture(image, TexCoord + vec2(texOffset.x * i, 0.0)).rgb * weight[i];
            result += texture(image, TexCoord - vec2(texOffset.x * i, 0.0)).rgb * weight[i];
        }
    } else {
        for (int i = 1; i < 5; i++) {
            result += texture(image, TexCoord + vec2(0.0, texOffset.y * i)).rgb * weight[i];
            result += texture(image, TexCoord - vec2(0.0, texOffset.y * i)).rgb * weight[i];
        }
    }
    FragColor = vec4(result, 1.0);
}