#version 330

#moj_import <minecraft:dynamictransforms.glsl>

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

vec3 rainbow(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

float box(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec2 code = floor(texCoord0 / 4.0);
    vec2 t = (texCoord0 - code * 4.0 - 1.0) * 0.5;
    vec2 size = 1.0 / max(fwidth(t), vec2(0.00001));
    vec2 p = (t - 0.5) * size;
    float r = min(code.x / 500.0 * size.y, min(size.x, size.y) * 0.5);
    float e = code.y / 500.0 * size.y;
    vec4 color = vertexColor;
    float a;
#if MODE == 1
    float d = box(p, size * 0.5, r);
    a = clamp(0.5 - d, 0.0, 1.0) * clamp(0.5 + d + e, 0.0, 1.0);
#elif MODE == 2
    vec2 hb = max(size * 0.5 - e, vec2(0.5));
    float d = box(p, hb, min(r, min(hb.x, hb.y)));
    float k = clamp(d / max(e, 0.5), 0.0, 1.0);
    a = (1.0 - k) * (1.0 - k) * (1.0 - k * 0.5);
#elif MODE == 3
    color.rgb = rainbow(t.x);
    a = clamp(0.5 - box(p, size * 0.5, r), 0.0, 1.0);
#elif MODE == 4
    color.rgb = mix(vec3(1.0), vertexColor.rgb, t.x) * (1.0 - t.y);
    a = clamp(0.5 - box(p, size * 0.5, r), 0.0, 1.0);
#else
    a = clamp(0.5 - box(p, size * 0.5, r), 0.0, 1.0);
#endif
    color.a *= a;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
