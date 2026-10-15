package dev.htcl;

public final class Colors {
    private Colors() {
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static int r(int c) {
        return c >> 16 & 255;
    }

    public static int g(int c) {
        return c >> 8 & 255;
    }

    public static int b(int c) {
        return c & 255;
    }

    public static int rgb(float r, float g, float b) {
        return Math.round(clamp(r, 0f, 1f) * 255f) << 16 | Math.round(clamp(g, 0f, 1f) * 255f) << 8 | Math.round(clamp(b, 0f, 1f) * 255f);
    }

    public static int argb(int alpha, int rgb) {
        return (alpha & 255) << 24 | rgb & 0xFFFFFF;
    }

    public static int alpha(int argb, float k) {
        int a = Math.round((argb >>> 24) * clamp(k, 0f, 1f));
        return a << 24 | argb & 0xFFFFFF;
    }

    public static int mix(int a, int b, float t) {
        t = clamp(t, 0f, 1f);
        int aa = a >>> 24, ba = b >>> 24;
        return Math.round(aa + (ba - aa) * t) << 24
            | Math.round(r(a) + (r(b) - r(a)) * t) << 16
            | Math.round(g(a) + (g(b) - g(a)) * t) << 8
            | Math.round(b(a) + (b(b) - b(a)) * t);
    }

    public static float[] hsv(int c) {
        float r = r(c) / 255f, g = g(c) / 255f, b = b(c) / 255f;
        float hi = Math.max(r, Math.max(g, b)), lo = Math.min(r, Math.min(g, b)), d = hi - lo;
        float h = 0f;
        if (d > 1e-6f) {
            if (hi == r) h = ((g - b) / d) % 6f;
            else if (hi == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0f) h += 1f;
        }
        return new float[]{h, hi <= 0f ? 0f : d / hi, hi};
    }

    public static int fromHsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float r = channel(h, 0f), g = channel(h, 4f), b = channel(h, 2f);
        s = clamp(s, 0f, 1f);
        v = clamp(v, 0f, 1f);
        return rgb(v * (1f - s + s * r), v * (1f - s + s * g), v * (1f - s + s * b));
    }

    private static float channel(float h, float shift) {
        float k = (h * 6f + shift) % 6f;
        return clamp(Math.abs(k - 3f) - 1f, 0f, 1f);
    }

    public static int shift(int c, float turns) {
        float[] v = hsv(c);
        return fromHsv(v[0] + turns, v[1], v[2]);
    }

    public static String hex(int c) {
        return String.format("#%06X", c & 0xFFFFFF);
    }

    public static int parse(String s, int fallback) {
        if (s == null) return fallback;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 3) {
            t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
        }
        if (t.length() != 6) return fallback;
        try {
            return Integer.parseInt(t, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static float luma(int c) {
        return (0.2126f * r(c) + 0.7152f * g(c) + 0.0722f * b(c)) / 255f;
    }
}
