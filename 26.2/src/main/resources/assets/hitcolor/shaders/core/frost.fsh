#version 330

uniform sampler2D InSampler;

layout(std140) uniform Pass {
    vec4 A;
    vec4 B;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float grey = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(grey), c, A.y) * A.x;
    float n = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
    fragColor = vec4(c + n / 255.0, 1.0);
}
