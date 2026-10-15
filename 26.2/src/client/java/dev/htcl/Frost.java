package dev.htcl;

public final class Frost {
    private static final int LEVELS = 3;
    private static final Gpu.Tex[] down = new Gpu.Tex[LEVELS];
    private static Gpu.Tex spare;
    private static int width;
    private static int height;
    private static boolean want;
    private static Gpu.Pipe shrink;
    private static Gpu.Pipe blur;
    private static Gpu.Pipe show;
    private static Gpu.Buf[] ubo;

    private Frost() {
    }

    public static void request() {
        want = true;
    }

    public static boolean run() {
        if (!want) return false;
        want = false;
        setup();
        Gpu.Tex screen = Gpu.screen();
        int w = screen.width();
        int h = screen.height();
        if (w < 16 || h < 16) return false;
        size(w, h);
        Gpu.Tex src = screen;
        int sw = w;
        int sh = h;
        for (int i = 0; i < LEVELS; i++) {
            Gpu.write(ubo[i], 1f / sw, 1f / sh, 0, 0, 0, 0, 0, 0);
            Gpu.draw(shrink, down[i], "Pass", ubo[i], src, null);
            src = down[i];
            sw = Math.max(1, sw >> 1);
            sh = Math.max(1, sh >> 1);
        }
        Gpu.Tex last = down[LEVELS - 1];
        float s = 3.2f;
        Gpu.write(ubo[LEVELS], 1f / sw, 0f, 0, 0, s, s, 10, 0);
        Gpu.draw(blur, spare, "Pass", ubo[LEVELS], last, null);
        Gpu.write(ubo[LEVELS + 1], 0f, 1f / sh, 0, 0, s, s, 10, 0);
        Gpu.draw(blur, last, "Pass", ubo[LEVELS + 1], spare, null);
        Gpu.write(ubo[LEVELS + 2], 0.78f, 1.12f, 0, 0, 0, 0, 0, 0);
        Gpu.draw(show, screen, "Pass", ubo[LEVELS + 2], last, null);
        return true;
    }

    private static void setup() {
        if (shrink != null) return;
        shrink = Gpu.post("down", "Pass", Gpu.NONE, false);
        blur = Gpu.post("blur", "Pass", Gpu.NONE, false);
        show = Gpu.post("frost", "Pass", Gpu.NONE, false);
        ubo = new Gpu.Buf[LEVELS + 3];
        for (int i = 0; i < ubo.length; i++) ubo[i] = Gpu.ubo("hitcolor frost " + i, 32);
    }

    private static void size(int w, int h) {
        if (w == width && h == height && spare != null) return;
        for (int i = 0; i < LEVELS; i++) {
            if (down[i] != null) down[i].close();
            down[i] = Gpu.texture("hitcolor frost " + i, Math.max(1, w >> (i + 1)), Math.max(1, h >> (i + 1)));
        }
        if (spare != null) spare.close();
        spare = Gpu.texture("hitcolor frost blur", Math.max(1, w >> LEVELS), Math.max(1, h >> LEVELS));
        width = w;
        height = h;
    }
}
