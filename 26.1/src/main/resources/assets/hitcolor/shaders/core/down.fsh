#version 330

uniform sampler2D InSampler;

layout(std140) uniform Pass {
    vec4 A;
    vec4 B;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 t = A.xy;
    vec4 c = texture(InSampler, texCoord + vec2(-t.x, -t.y))
        + texture(InSampler, texCoord + vec2(t.x, -t.y))
        + texture(InSampler, texCoord + vec2(-t.x, t.y))
        + texture(InSampler, texCoord + vec2(t.x, t.y));
    fragColor = c * 0.25;
}
