#version 330

uniform sampler2D InSampler;

layout(std140) uniform Pass {
    vec4 A;
    vec4 B;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 s = max(B.xy, vec2(0.05));
    vec2 k = -0.5 / (s * s);
    int n = int(B.z);
    vec3 sum = texture(InSampler, texCoord).rgb;
    vec3 w = vec3(1.0);
    for (int i = 1; i <= 96; i++) {
        if (i > n) {
            break;
        }
        float x = float(i);
        vec2 g = exp(k * x * x);
        vec3 gw = vec3(g.x, g.y, g.x);
        vec3 a = texture(InSampler, texCoord + A.xy * x).rgb + texture(InSampler, texCoord - A.xy * x).rgb;
        sum += a * gw;
        w += 2.0 * gw;
    }
    fragColor = vec4(sum / w, 1.0);
}
