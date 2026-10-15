package dev.htcl.ui;

import dev.htcl.Cfg;
import dev.htcl.Colors;
import dev.htcl.Hc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class Menu {
    public static final int BACK = 1;
    public static final int ENTER = 2;
    public static final int ESCAPE = 3;
    public static final int TAB = 4;
    public static final int PASTE = 5;
    public static final int COPY = 6;
    private static final int INK = 0xFF22201C;
    private static final int SOFT = 0xFF857F73;
    private static final int WELL = 0x14000000;
    private static final int EDGE = 0x2A000000;
    private static final int PAPER_TOP = 0xFFF8F3E9;
    private static final int PAPER_BOT = 0xFFEAE3D4;
    private static final String[] TABS = {"Color", "Gradient", "Effect", "Show"};
    private static final String[] CURVES = {"Hold", "Fade", "Late", "Early"};
    private static final String[] CURVE_TIPS = {"Stays at full strength, then stops", "Fades out evenly", "Stays strong, drops at the end", "Drops fast, then lingers"};
    private static final String[] STOPS = {"Off", "2 colors", "3 colors"};
    private static final String[] STOP_NAMES = {"Feet", "Middle", "Head"};

    public interface Drag {
        void to(double x, double y);
    }

    private record Hit(String id, float x, float y, float w, float h, Runnable click, Drag drag, DoubleConsumer scroll) {
        boolean in(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    private final Cfg start;
    private final Runnable close;
    private final Supplier<String> clipboard;
    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Float> fx = new HashMap<>();
    private final float[] hsvA;
    private final float[] hsvB;
    private final float[] hsvC;
    private String active;
    private Drag drag;
    private int tab;
    private int gsel;
    private String typing;
    private String hex = "";
    private float open;
    private long last;
    private float dt;
    private float clock;
    private float hitAt = -9f;
    private int sig;
    private double mx;
    private double my;

    public Menu(Runnable close, Supplier<String> clipboard) {
        this.close = close;
        this.clipboard = clipboard;
        this.start = Cfg.c.copy();
        this.hsvA = Colors.hsv(Cfg.c.col);
        this.hsvB = Colors.hsv(Cfg.c.col2);
        this.hsvC = Colors.hsv(Cfg.c.colm);
    }

    public void cancel() {
        Cfg.c = start.copy();
    }

    public void save() {
        commitHex();
        Cfg.sv();
    }

    public void draw(Canvas g, int width, int height, double mouseX, double mouseY) {
        long now = System.nanoTime();
        dt = last == 0L ? 0f : Math.min((now - last) / 1e9f, 0.1f);
        last = now;
        clock += dt;
        mx = mouseX;
        my = mouseY;
        hits.clear();
        open = ease(open, 1f, 8f);
        Cfg c = Cfg.c;

        int h2 = java.util.Objects.hash(c.col, c.col2, c.colm, c.grad, c.stops, c.bias, c.str, c.endStr, c.curve, c.pulses, c.ar, c.arStr, c.on);
        if (h2 != sig) {
            if (sig != 0) hitAt = -9f;
            sig = h2;
        }

        float pw = Math.max(240, Math.min(300, width * 0.42f));
        float ph = height - 20;
        float px = width - pw - 10 + (1f - open) * 40f;
        float py = 10;

        stage(g, c, px - 10, height);

        g.shadow(px, py + 6, pw, ph, 16, 26, fade(0x80000000));
        g.fill(px, py, pw, ph, 16, fade(PAPER_TOP), fade(PAPER_BOT));
        g.ring(px, py, pw, ph, 16, 1, fade(0x55FFFFFF), fade(0x22000000));

        float pad = 14;
        float cw = pw - pad * 2;
        float cx = px + pad;

        g.text("Hit Color", cx, py + 16, fade(INK));
        float tx = px + pw - pad - 22;
        Runnable flip = () -> c.on = !c.on;
        String st = c.on ? "On" : "Off";
        g.text(st, tx - 6 - g.width(st), py + 16, fade(c.on ? INK : SOFT));
        toggle(g, "master", tx, py + 14, c.on, flip);

        segmented(g, "tabs", cx, py + 34, cw, 18, TABS, tab, i -> {
            commitHex();
            tab = i;
        });

        float top = py + 62;
        float bot = py + ph - 38;
        float space = bot - top;
        switch (tab) {
            case 0 -> color(g, c, cx, top, cw, space);
            case 1 -> gradient(g, c, cx, top, cw, space);
            case 2 -> effect(g, c, cx, top, cw, space);
            default -> show(g, c, cx, top, cw, space);
        }

        float by = py + ph - 30;
        button(g, "reset", "Defaults", cx, by, 62, false, () -> {
            Cfg.c = new Cfg();
            System.arraycopy(Colors.hsv(Cfg.c.col), 0, hsvA, 0, 3);
            System.arraycopy(Colors.hsv(Cfg.c.col2), 0, hsvB, 0, 3);
            System.arraycopy(Colors.hsv(Cfg.c.colm), 0, hsvC, 0, 3);
        });
        float right = px + pw - pad;
        button(g, "done", "Done", right - 58, by, 58, true, () -> {
            save();
            close.run();
        });
        button(g, "cancel", "Cancel", right - 58 * 2 - 6, by, 58, false, () -> {
            cancel();
            close.run();
        });
    }

    private void stage(Canvas g, Cfg c, float areaW, int height) {
        if (clock - hitAt > 3f) {
            hitAt = clock;
            g.hurt();
        }
        float fh = Math.max(70f, Math.min(height - 70f, 250f));
        float fw = fh * 0.62f;
        float x0 = (areaW - fw) / 2f;
        float y0 = (height - fh) / 2f + 6f;
        boolean real = g.player(x0, y0, fw, fh);
        if (!real) {
            float p = Math.min(1f, (clock - hitAt) / 0.5f);
            float a = c.on && p < 1f ? Hc.st(p) : 0f;
            float u = fh / 32f;
            float fx0 = areaW / 2f - 8 * u;
            float[][] parts = {{4, 0, 8, 8}, {4, 8, 8, 12}, {0, 8, 4, 12}, {12, 8, 4, 12}, {4, 20, 4, 12}, {8, 20, 4, 12}};
            for (float[] q : parts) {
                float qx = fx0 + q[0] * u, qy = y0 + q[1] * u, qw = q[2] * u, qh = q[3] * u;
                g.fill(qx, qy, qw, qh, 2, fade(0xFF9AA0AC), fade(0xFF767B87));
                float t1 = 1f - (qy - y0) / fh;
                float t0 = 1f - (qy + qh - y0) / fh;
                g.fill(qx, qy, qw, qh, 2, fade(Colors.alpha(0xFF000000 | Hc.gc(t1), a)), fade(Colors.alpha(0xFF000000 | Hc.gc(t0), a)));
            }
        }
        click("figure", x0, y0, fw, fh, () -> hitAt = -9f);
        String hint = "click to hit";
        float hx = areaW / 2f - g.width(hint) / 2f;
        g.text(hint, hx + 1, y0 - 12, fade(0xAA000000));
        g.text(hint, hx, y0 - 13, fade(0xFFFFFFFF));
    }

    private void click(String id, float x, float y, float w, float h, Runnable run) {
        hits.add(new Hit(id, x, y, w, h, run, null, null));
    }

    private void color(Canvas g, Cfg c, float x, float y, float w, float space) {
        float ph = Math.max(34, Math.min(70, space - 56));
        g.text("HIT COLOR", x, y, fade(SOFT));
        picker(g, "a", () -> c.col, v -> c.col = v, hsvA, x, y + 12, w, ph);
        slider(g, "str", "Strength", x, y + 12 + ph + 14, w, c.str, 0f, 100f, 1f, v -> c.str = (int) v, c.str + "%");
    }

    private void gradient(Canvas g, Cfg c, float x, float y, float w, float space) {
        segmented(g, "stops", x, y, w, 18, STOPS, c.grad ? c.stops - 1 : 0, i -> {
            c.grad = i > 0;
            if (i > 0) c.stops = i + 1;
        });
        float y2 = y + 24;
        int n = c.stops;
        if (n == 2 && gsel == 1) gsel = 2;
        int[] idx = n == 2 ? new int[]{0, 2} : new int[]{0, 1, 2};
        float sg = 6;
        float sw = (w - sg * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            int s = idx[i];
            int col = s == 0 ? c.col : s == 1 ? c.colm : c.col2;
            float sx = x + i * (sw + sg);
            boolean sel = s == gsel;
            boolean over = hover(sx, y2, sw, 20);
            float k = anim("sw" + s, sel ? 1f : over ? 0.4f : 0f, 14f);
            g.fill(sx, y2, sw, 20, 8, fade(0xFF000000 | Colors.mix(0xFF000000 | col, 0xFFFFFFFF, 0.12f)), fade(0xFF000000 | col));
            g.ring(sx - 1, y2 - 1, sw + 2, 22, 9, 1.5f, fade(Colors.alpha(INK, 0.15f + 0.85f * k)));
            int tc = Colors.luma(col) > 0.6f ? 0xFF22201C : 0xFFFFFFFF;
            g.text(STOP_NAMES[s], sx + (sw - g.width(STOP_NAMES[s])) / 2f, y2 + 6, fade(tc));
            int ss = s;
            hits.add(new Hit("swatch" + s, sx, y2, sw, 20, () -> {
                commitHex();
                gsel = ss;
            }, null, null));
        }
        float y3 = y2 + 28;
        float used = y3 - y;
        float ph = Math.max(28, Math.min(54, space - used - (n == 3 ? 32 : 4)));
        int sel = gsel;
        if (sel == 0) picker(g, "a", () -> c.col, v -> c.col = v, hsvA, x, y3, w, ph);
        else if (sel == 1) picker(g, "c", () -> c.colm, v -> c.colm = v, hsvC, x, y3, w, ph);
        else picker(g, "b", () -> c.col2, v -> c.col2 = v, hsvB, x, y3, w, ph);
        if (n == 3) slider(g, "bias", "Middle position", x, y3 + ph + 8, w, c.bias, 10f, 90f, 1f, v -> c.bias = (int) v, c.bias + "%");
        float dim = anim("gdim", c.grad ? 0f : 1f, 12f);
        if (dim > 0.01f) {
            float dy = y + 22;
            g.fill(x - 6, dy, w + 12, space - 22, 10, fade(Colors.alpha(0xD2F1ECDF, dim)));
            g.text("pick 2 or 3 colors above to turn it on", x + (w - g.width("pick 2 or 3 colors above to turn it on")) / 2f, dy + 30, fade(Colors.alpha(SOFT, dim)));
            hits.add(new Hit("gblock", x - 6, dy, w + 12, space - 22, () -> {
            }, null, null));
        }
    }

    private void effect(Canvas g, Cfg c, float x, float y, float w, float space) {
        float dim = anim("hold", c.curve == 0 ? 1f : 0f, 12f);
        slider(g, "end", "Strength at the end", x, y, w, c.endStr, 0f, 100f, 1f, v -> c.endStr = (int) v, c.endStr + "%");
        if (dim > 0.01f) {
            g.fill(x - 4, y - 3, w + 8, 30, 8, fade(Colors.alpha(0xB8F1ECDF, dim)));
            hits.add(new Hit("endblock", x - 4, y - 3, w + 8, 30, () -> {
            }, null, null));
        }
        slider(g, "pulse", "Pulses", x, y + 32, w, c.pulses, 0f, 4f, 1f, v -> c.pulses = (int) v, c.pulses == 0 ? "Off" : String.valueOf(c.pulses));
        g.text("FADE CURVE", x, y + 64, fade(SOFT));
        segmented(g, "curve", x, y + 76, w, 18, CURVES, c.curve, i -> c.curve = i);
        g.text(CURVE_TIPS[c.curve], x, y + 100, fade(SOFT));
    }

    private void show(Canvas g, Cfg c, float x, float y, float w, float space) {
        float step = 24;
        row(g, "pl", "Players", x, y, w, c.pl, () -> c.pl = !c.pl);
        row(g, "mb", "Mobs", x, y + step, w, c.mb, () -> c.mb = !c.mb);
        row(g, "sf", "Yourself", x, y + step * 2, w, c.sf, () -> c.sf = !c.sf);
        row(g, "ar", "Tint armor too", x, y + step * 3, w, c.ar, () -> c.ar = !c.ar);
        float dim = anim("ardim", c.ar ? 0f : 1f, 12f);
        slider(g, "arstr", "Armor strength", x, y + step * 4 + 4, w, c.arStr, 0f, 100f, 1f, v -> c.arStr = (int) v, c.arStr + "%");
        if (dim > 0.01f) {
            g.fill(x - 4, y + step * 4 + 1, w + 8, 30, 8, fade(Colors.alpha(0xB8F1ECDF, dim)));
            hits.add(new Hit("arblock", x - 4, y + step * 4 + 1, w + 8, 30, () -> {
            }, null, null));
        }
    }

    private void row(Canvas g, String id, String label, float x, float y, float w, boolean on, Runnable flip) {
        boolean over = hover(x - 4, y - 3, w + 8, 20);
        float k = anim(id + "row", over ? 1f : 0f, 14f);
        if (k > 0.01f) g.fill(x - 4, y - 3, w + 8, 20, 8, fade(Colors.alpha(0x0E000000, k)));
        g.text(label, x + 2, y + 2, fade(on ? INK : SOFT));
        toggle(g, id, x + w - 24, y, on, flip);
        hits.add(new Hit(id + "row", x - 4, y - 3, w + 8, 20, flip, null, null));
    }

    private void toggle(Canvas g, String id, float x, float y, boolean on, Runnable flip) {
        float w = 22;
        float h = 12;
        float t = anim(id, on ? 1f : 0f, 14f);
        int acc = accent();
        g.fill(x, y, w, h, h / 2f, fade(Colors.mix(0x26000000, Colors.mix(acc, 0xFFFFFFFF, 0.1f), t)), fade(Colors.mix(0x3A000000, acc, t)));
        g.ring(x, y, w, h, h / 2f, 1, fade(0x33000000), fade(0x0F000000));
        float k = 8;
        float kx = x + 2 + (w - 4 - k) * t;
        float ky = y + (h - k) / 2f;
        g.shadow(kx, ky + 0.5f, k, k, k / 2f, 2, fade(0x55000000));
        g.fill(kx, ky, k, k, k / 2f, fade(0xFFFFFFFF), fade(0xFFEDEDED));
        hits.add(new Hit(id, x - 2, y - 2, w + 4, h + 4, flip, null, null));
    }

    private void slider(Canvas g, String id, String label, float x, float y, float w, float v, float lo, float hi, float step, DoubleConsumer set, String shown) {
        g.text(label, x, y, fade(SOFT));
        g.text(shown, x + w - g.width(shown), y, fade(INK));
        float ty = y + 12;
        float th = 5;
        float t = Colors.clamp((v - lo) / (hi - lo), 0f, 1f);
        boolean held = id.equals(active);
        float k = anim(id, held || hover(x - 4, ty - 6, w + 8, th + 12) ? 1f : 0f, 16f);
        float at = anim(id + "v", t, 22f);
        int acc = accent();
        g.fill(x, ty, w, th, th / 2f, fade(0x22000000));
        g.fill(x, ty, Math.max(th, w * at), th, th / 2f, fade(Colors.mix(acc, 0xFFFFFFFF, 0.2f)), fade(acc));
        float s = 9 + k * 2;
        float kx = x + w * at - s / 2f;
        float ky = ty + th / 2f - s / 2f;
        g.shadow(kx, ky + 0.5f, s, s, s / 2f, 2.5f, fade(0x66000000));
        g.fill(kx, ky, s, s, s / 2f, fade(0xFFFFFFFF), fade(0xFFE9E9E9));
        g.ring(kx, ky, s, s, s / 2f, 1, fade(0x30000000));
        Drag d = (px, py) -> set.accept(snap(lo + (hi - lo) * Colors.clamp((float) ((px - x) / w), 0f, 1f), step, lo, hi));
        hits.add(new Hit(id, x - 4, ty - 7, w + 8, th + 14, null, d, a -> set.accept(snap(v + (float) Math.signum(a) * Math.max(step, (hi - lo) / 20f), step, lo, hi))));
    }

    private void segmented(Canvas g, String id, float x, float y, float w, float h, String[] items, int sel, IntConsumer pick) {
        g.fill(x, y, w, h, h / 2f, fade(WELL));
        g.ring(x, y, w, h, h / 2f, 1, fade(0x1E000000), fade(0x0A000000));
        int n = items.length;
        float[] edge = new float[n + 1];
        float sum = 0;
        for (String s : items) sum += g.width(s);
        float pad = (w - sum) / n;
        edge[0] = x;
        for (int i = 0; i < n; i++) edge[i + 1] = edge[i] + g.width(items[i]) + pad;
        edge[n] = x + w;
        float px = anim(id, edge[sel], 16f);
        float pw = anim(id + "w", edge[sel + 1] - edge[sel], 16f);
        g.shadow(px + 2, y + 2, pw - 4, h - 4, (h - 4) / 2f, 3, fade(0x40000000));
        g.fill(px + 2, y + 2, pw - 4, h - 4, (h - 4) / 2f, fade(0xFF4A473F), fade(INK));
        for (int i = 0; i < n; i++) {
            float ix = edge[i];
            float iw = edge[i + 1] - ix;
            boolean over = hover(ix, y, iw, h);
            float k = anim(id + i, over ? 1f : 0f, 14f);
            int col = i == sel ? 0xFFFFFFFF : Colors.mix(SOFT, INK, k * 0.7f);
            g.text(items[i], ix + (iw - g.width(items[i])) / 2f, y + (h - 8) / 2f, fade(col));
            int idx = i;
            hits.add(new Hit(id + i, ix, y, iw, h, () -> pick.accept(idx), null, a -> pick.accept(Math.floorMod(sel - (int) Math.signum(a), n))));
        }
    }

    private void button(Canvas g, String id, String label, float x, float y, float w, boolean main, Runnable run) {
        float h = 20;
        boolean over = hover(x, y, w, h);
        float k = anim(id, over ? 1f : 0f, 14f);
        if (main) {
            g.shadow(x, y + 1, w, h, 10, 5, fade(0x55000000));
            g.fill(x, y, w, h, 10, fade(Colors.mix(0xFF4A473F, 0xFF66625A, k)), fade(Colors.mix(INK, 0xFF3A372F, k)));
            g.text(label, x + (w - g.width(label)) / 2f, y + 6, fade(0xFFFFFFFF));
        } else {
            g.fill(x, y, w, h, 10, fade(Colors.mix(0x00000000, 0x16000000, k)));
            g.ring(x, y, w, h, 10, 1, fade(Colors.mix(0x40000000, 0x66000000, k)));
            g.text(label, x + (w - g.width(label)) / 2f, y + 6, fade(INK));
        }
        hits.add(new Hit(id, x, y, w, h, run, null, null));
    }

    private void picker(Canvas g, String id, IntSupplier get, IntConsumer set, float[] hsv, float x, float y, float w, float h) {
        int col = get.getAsInt();
        float sw = Math.round(w * 0.46f);
        int hueCol = 0xFF000000 | Colors.fromHsv(hsv[0], 1f, 1f);
        g.shadow(x, y + 1, sw, h, 7, 5, fade(0x44000000));
        g.sv(x, y, sw, h, 7, fade(hueCol));
        g.ring(x, y, sw, h, 7, 1, fade(0x33000000), fade(0x14000000));
        float mxp = x + hsv[1] * sw;
        float myp = y + (1f - hsv[2]) * h;
        g.shadow(mxp - 4.5f, myp - 4.5f, 9, 9, 4.5f, 2, fade(0x90000000));
        g.fill(mxp - 4.5f, myp - 4.5f, 9, 9, 4.5f, fade(0xFFFFFFFF));
        g.fill(mxp - 3, myp - 3, 6, 6, 3, fade(0xFF000000 | col));
        hits.add(new Hit(id + "sv", x, y, sw, h, null, (px, py) -> {
            hsv[1] = Colors.clamp((float) ((px - x) / sw), 0f, 1f);
            hsv[2] = 1f - Colors.clamp((float) ((py - y) / h), 0f, 1f);
            set.accept(Colors.fromHsv(hsv[0], hsv[1], hsv[2]));
            typing = null;
        }, null));

        float rx = x + sw + 10;
        float rw = w - sw - 10;
        g.shadow(rx, y + 1, rw, 12, 6, 3, fade(0x40000000));
        g.hue(rx, y, rw, 12, 6, fade(0xFFFFFFFF));
        g.ring(rx, y, rw, 12, 6, 1, fade(0x33000000), fade(0x14000000));
        float hx = rx + hsv[0] * rw;
        g.shadow(hx - 3.5f, y - 2, 7, 16, 3.5f, 2, fade(0x70000000));
        g.fill(hx - 3.5f, y - 2, 7, 16, 3.5f, fade(0xFFFFFFFF), fade(0xFFE9E9E9));
        g.fill(hx - 2, y + 0.5f, 4, 11, 2, fade(hueCol));
        hits.add(new Hit(id + "hue", rx - 3, y - 3, rw + 6, 18, null, (px, py) -> {
            hsv[0] = Colors.clamp((float) ((px - rx) / rw), 0f, 0.9999f);
            set.accept(Colors.fromHsv(hsv[0], hsv[1], hsv[2]));
            typing = null;
        }, a -> {
            hsv[0] = (hsv[0] + (float) Math.signum(a) * 0.01f + 1f) % 1f;
            set.accept(Colors.fromHsv(hsv[0], hsv[1], hsv[2]));
        }));

        float fy = y + 20;
        float sq = 18;
        boolean focus = id.equals(typing) && active == null;
        float k = anim(id + "hex", focus ? 1f : hover(rx, fy, rw, sq) ? 0.4f : 0f, 14f);
        g.fill(rx, fy, rw, sq, 7, fade(WELL), fade(0x1E000000));
        g.ring(rx, fy, rw, sq, 7, 1, fade(Colors.mix(EDGE, accent(), k)), fade(Colors.mix(0x14000000, accent(), k * 0.5f)));
        String shown = focus ? hex : Colors.hex(col).substring(1);
        g.fill(rx + 5, fy + 5, 8, 8, 4, fade(0xFF000000 | col));
        g.ring(rx + 5, fy + 5, 8, 8, 4, 1, fade(0x44000000));
        g.text(shown, rx + 17, fy + 5, fade(focus && !valid(hex) ? 0xFFD03030 : INK));
        if (focus && (clock % 1f) < 0.55f) {
            float cx = rx + 17 + g.width(shown) + 1;
            g.fill(cx, fy + 4, 1, 10, 0, fade(INK));
        }
        hits.add(new Hit(id + "hexf", rx, fy, rw, sq, () -> {
            typing = id;
            hex = Colors.hex(get.getAsInt()).substring(1);
        }, null, null));
    }

    private void commitHex() {
        if (typing == null) return;
        String id = typing;
        typing = null;
        if (valid(hex)) apply(id, hex);
    }

    private void apply(String id, String s) {
        Cfg c = Cfg.c;
        int col = Colors.parse(s, id.equals("a") ? c.col : id.equals("b") ? c.col2 : c.colm);
        if (id.equals("a")) {
            c.col = col;
            System.arraycopy(Colors.hsv(col), 0, hsvA, 0, 3);
        } else if (id.equals("b")) {
            c.col2 = col;
            System.arraycopy(Colors.hsv(col), 0, hsvB, 0, 3);
        } else {
            c.colm = col;
            System.arraycopy(Colors.hsv(col), 0, hsvC, 0, 3);
        }
    }

    private static boolean valid(String s) {
        return (s.length() == 6 || s.length() == 3) && s.chars().allMatch(ch -> Character.digit(ch, 16) >= 0);
    }

    public boolean click(double x, double y, int button) {
        mx = x;
        my = y;
        if (button != 0) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.in(x, y)) continue;
            if (!h.id.endsWith("hexf")) commitHex();
            if (h.drag != null) {
                active = h.id;
                drag = h.drag;
                drag.to(x, y);
            }
            if (h.click != null) h.click.run();
            return true;
        }
        commitHex();
        return false;
    }

    public boolean release(double x, double y, int button) {
        boolean was = drag != null;
        drag = null;
        active = null;
        return was;
    }

    public boolean dragged(double x, double y) {
        mx = x;
        my = y;
        if (drag == null) return false;
        drag.to(x, y);
        return true;
    }

    public boolean scroll(double x, double y, double amount) {
        if (amount == 0) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.scroll != null && h.in(x, y)) {
                h.scroll.accept(amount);
                return true;
            }
        }
        return false;
    }

    public boolean key(int action) {
        if (typing != null) {
            switch (action) {
                case BACK -> {
                    if (!hex.isEmpty()) hex = hex.substring(0, hex.length() - 1);
                    return true;
                }
                case ENTER -> {
                    commitHex();
                    return true;
                }
                case ESCAPE -> {
                    typing = null;
                    return true;
                }
                case PASTE -> {
                    String s = clipboard.get();
                    if (s != null) {
                        String t = s.trim().replace("#", "");
                        if (valid(t)) {
                            hex = t.toUpperCase(Locale.ROOT);
                            apply(typing, hex);
                        }
                    }
                    return true;
                }
                case COPY -> {
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }
        return false;
    }

    public boolean type(int codepoint) {
        if (typing == null) return false;
        if (Character.digit(codepoint, 16) < 0) return true;
        if (hex.length() < 6) {
            hex = hex + Character.toUpperCase((char) codepoint);
            if (hex.length() == 6) apply(typing, hex);
        }
        return true;
    }

    public boolean typing() {
        return typing != null;
    }

    private int accent() {
        int b = Cfg.c.col;
        int col = 0xFF000000 | b;
        return Colors.luma(b) > 0.8f ? 0xFF000000 | Colors.mix(col, 0xFF000000, 0.35f) : col;
    }

    private boolean hover(float x, float y, float w, float h) {
        return drag == null && mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private float anim(String key, float target, float speed) {
        Float cur = fx.get(key);
        float v = cur == null ? target : ease(cur, target, speed);
        fx.put(key, v);
        return v;
    }

    private float ease(float cur, float to, float speed) {
        float v = cur + (to - cur) * (1f - (float) Math.exp(-speed * dt));
        return Math.abs(to - v) < 0.0005f ? to : v;
    }

    private int fade(int argb) {
        return Colors.alpha(argb, open);
    }

    private static float snap(float v, float step, float lo, float hi) {
        float s = Math.round(v / step) * step;
        return Colors.clamp(s, lo, hi);
    }
}
