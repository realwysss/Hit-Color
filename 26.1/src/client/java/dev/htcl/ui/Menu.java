package dev.htcl.ui;

import dev.htcl.Cfg;
import dev.htcl.Colors;
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
    private static final int TEXT = 0xFFF3F4F8;
    private static final int MUTED = 0xFF9BA1AE;
    private static final int WELL = 0x42000000;
    private static final int EDGE = 0x21FFFFFF;
    private static final int[] BACKDROPS = {0xFF0C0D12, 0xFF3A1F1A, 0xFF2E4A66, 0xFFD8D2C4};
    private static final String[] BACKDROP_NAMES = {"Night", "Nether", "Sky", "Sand"};
    private static final String[] TABS = {"Color", "Gradient", "Show"};

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
    private String active;
    private Drag drag;
    private int tab;
    private int backdrop;
    private String typing;
    private String hex = "";
    private float open;
    private long last;
    private float dt;
    private float clock;
    private double mx;
    private double my;
    private String tip;

    public Menu(Runnable close, Supplier<String> clipboard) {
        this.close = close;
        this.clipboard = clipboard;
        this.start = Cfg.c.copy();
        this.hsvA = Colors.hsv(Cfg.c.col);
        this.hsvB = Colors.hsv(Cfg.c.col2);
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
        tip = null;
        open = ease(open, 1f, 9f);
        Cfg c = Cfg.c;

        float cw = Math.min(width - 24, 430);
        float ch = Math.min(height - 12, 252);
        float x = (width - cw) / 2f;
        float y = (height - ch) / 2f;
        int acc = accent();

        g.fill(0, 0, width, height, 0, fade(0x22000000), fade(0x7A000000));
        g.shadow(x + cw * 0.18f, y + ch * 0.12f, cw * 0.64f, ch * 0.64f, 60, 70, fade(Colors.alpha(acc, 0.13f)));
        g.shadow(x, y + 10, cw, ch, 16, 28, fade(0x99000000));
        g.fill(x, y, cw, ch, 14, fade(0xAE262833), fade(0xC20F1015));
        g.ring(x, y, cw, ch, 14, 1, fade(0x5CFFFFFF), fade(0x0FFFFFFF));

        header(g, c, x, y, cw);
        g.fill(x + 16, y + 32, cw - 32, 1, 0, fade(0x16FFFFFF));

        float top = y + 42;
        float bottom = y + ch - 36;
        float lw = Math.max(108, Math.min(146, cw * 0.34f));
        float lx = x + 14;
        preview(g, c, lx, top, lw, bottom - top);

        float rx = lx + lw + 12;
        float rw = x + cw - 14 - rx;
        segmented(g, "tabs", rx, top, rw, 18, TABS, tab, i -> {
            commitHex();
            tab = i;
        });
        float cy = top + 26;
        float space = bottom - cy;
        switch (tab) {
            case 0 -> color(g, c, rx, cy, rw, space);
            case 1 -> gradient(g, c, rx, cy, rw, space);
            default -> show(g, c, rx, cy, rw, space);
        }

        footer(g, x, y + ch - 28, cw);

        if (tip != null) {
            float tw = g.width(tip) + 12;
            float tx = (float) Math.min(mx + 10, width - tw - 4);
            float ty = (float) Math.max(my - 18, 2);
            g.shadow(tx, ty, tw, 15, 5, 6, fade(0x90000000));
            g.fill(tx, ty, tw, 15, 5, fade(0xEE2A2C36), fade(0xEE1B1C23));
            g.ring(tx, ty, tw, 15, 5, 1, fade(0x33FFFFFF), fade(0x0FFFFFFF));
            g.text(tip, tx + 6, ty + 4, fade(TEXT));
        }
    }

    private void header(Canvas g, Cfg c, float x, float y, float w) {
        g.text("Hit Color", x + 16, y + 12, fade(TEXT));
        String state = c.on ? "On" : "Off";
        float tx = x + w - 38;
        float lx = tx - 6 - g.width(state);
        Runnable flip = () -> c.on = !c.on;
        g.text(state, lx, y + 12, fade(c.on ? TEXT : MUTED));
        toggle(g, "master", tx, y + 10, c.on, flip);
        hits.add(new Hit("masterrow", lx - 4, y + 6, tx + 26 - (lx - 4), 18, flip, null, null));
    }

    private void preview(Canvas g, Cfg c, float x, float y, float w, float h) {
        g.fill(x, y, w, h, 10, fade(0x30000000), fade(0x58000000));
        float bgT = anim("bg", backdrop, 10f);
        int from = BACKDROPS[(int) Math.floor(bgT) % BACKDROPS.length];
        int to = BACKDROPS[(int) Math.ceil(bgT) % BACKDROPS.length];
        int bg = Colors.mix(from, to, bgT - (float) Math.floor(bgT));
        g.fill(x + 3, y + 3, w - 6, h - 6, 8, fade(Colors.alpha(bg, 0.9f)), fade(Colors.mix(bg, 0xFF000000, 0.35f)));
        g.ring(x, y, w, h, 10, 1, fade(0x38FFFFFF), fade(0x0AFFFFFF));
        int label = Colors.luma(bg) > 0.55f ? 0xAA000000 : 0x99FFFFFF;
        g.text("Preview", x + 10, y + 9, fade(label));

        float fh = Math.min(h - 44, 96f);
        float fw = fh * 0.5f;
        float fx0 = x + (w - fw) / 2f;
        float fy0 = y + 22 + (h - 44 - fh) / 2f;
        float head = fh * 0.22f;
        float body = fh * 0.38f;
        float legs = fh - head - body - 2f;
        float pulse = c.fade ? 0.6f + 0.4f * (float) Math.sin(clock * 3f) : 1f;
        float[][] parts = {{fx0 + fw * 0.2f, fy0, fw * 0.6f, head}, {fx0, fy0 + head + 1f, fw, body}, {fx0 + fw * 0.1f, fy0 + head + body + 2f, fw * 0.8f, legs}};
        for (float[] p : parts) {
            g.fill(p[0], p[1], p[2], p[3], 3, fade(0xFF8D93A0), fade(0xFF6C717D));
            float t0 = 1f - (p[1] + p[3] - fy0) / fh;
            float t1 = 1f - (p[1] - fy0) / fh;
            float a = Colors.clamp(c.str / 100f * pulse, 0f, 1f) * (c.on ? 1f : 0.2f);
            int cTop = c.grad ? Colors.mix(0xFF000000 | c.col, 0xFF000000 | c.col2, t1) : 0xFF000000 | c.col;
            int cBot = c.grad ? Colors.mix(0xFF000000 | c.col, 0xFF000000 | c.col2, t0) : 0xFF000000 | c.col;
            g.fill(p[0], p[1], p[2], p[3], 3, fade(Colors.alpha(cTop, a)), fade(Colors.alpha(cBot, a)));
        }

        float d = 8;
        float gap = 7;
        float total = BACKDROPS.length * d + (BACKDROPS.length - 1) * gap;
        float dx = x + (w - total) / 2f;
        float dy = y + h - 17;
        for (int i = 0; i < BACKDROPS.length; i++) {
            float px = dx + i * (d + gap);
            boolean over = hover(px - 3, dy - 3, d + 6, d + 6);
            float kk = anim("dot" + i, over ? 1f : 0f, 16f);
            float s = d + kk * 2;
            g.fill(px - kk, dy - kk, s, s, s / 2f, fade(BACKDROPS[i]));
            g.ring(px - kk - 1.5f, dy - kk - 1.5f, s + 3, s + 3, (s + 3) / 2f, 1, fade(i == backdrop ? 0xDDFFFFFF : 0x40FFFFFF));
            if (over) tip = BACKDROP_NAMES[i];
            int idx = i;
            hits.add(new Hit("dot" + i, px - 3, dy - 3, d + 6, d + 6, () -> backdrop = idx, null, null));
        }
    }

    private void color(Canvas g, Cfg c, float x, float y, float w, float space) {
        float ph = Math.max(34, Math.min(66, space - 52));
        g.text(c.grad ? "Bottom color" : "Hit color", x, y - 1, fade(MUTED));
        picker(g, "a", () -> c.col, v -> c.col = v, hsvA, x, y + 10, w, ph);
        float sy = y + 10 + ph + 10;
        slider(g, "str", "Opacity", x, sy, w, c.str, 0f, 100f, 1f, v -> c.str = (int) v, c.str + "%");
    }

    private void gradient(Canvas g, Cfg c, float x, float y, float w, float space) {
        row(g, "grad", "Gradient", null, x, y, w, c.grad, () -> c.grad = !c.grad);
        float ph = Math.max(34, Math.min(66, space - 52));
        g.text("Top color", x, y + 20, fade(c.grad ? MUTED : 0xFF5C6070));
        picker(g, "b", () -> c.col2, v -> c.col2 = v, hsvB, x, y + 31, w, ph);
        if (!c.grad) g.fill(x - 4, y + 28, w + 8, ph + 24, 8, fade(0x88101218));
        if (!c.grad) hits.add(new Hit("gradblock", x - 4, y + 28, w + 8, ph + 24, () -> {
        }, null, null));
        g.text("Bottom is the first color, top is this one", x, y + 31 + ph + 8, fade(MUTED));
    }

    private void show(Canvas g, Cfg c, float x, float y, float w, float space) {
        float step = 18;
        float top = y + 1;
        row(g, "pl", "Players", null, x, top, w, c.pl, () -> c.pl = !c.pl);
        row(g, "mb", "Mobs", null, x, top + step, w, c.mb, () -> c.mb = !c.mb);
        row(g, "sf", "Yourself", null, x, top + step * 2, w, c.sf, () -> c.sf = !c.sf);
        row(g, "ar", "Armor", null, x, top + step * 3, w, c.ar, () -> c.ar = !c.ar);
        row(g, "fd", "Fade out", null, x, top + step * 4, w, c.fade, () -> c.fade = !c.fade);
        g.text("Armor gets a tint, it never shows the vanilla flash", x, top + step * 5 + 4, fade(MUTED));
    }

    private void footer(Canvas g, float x, float y, float w) {
        float bw = 60;
        float right = x + w - 14;
        button(g, "done", "Done", right - bw, y, bw, true, () -> {
            save();
            close.run();
        });
        button(g, "cancel", "Cancel", right - bw * 2 - 8, y, bw, false, () -> {
            cancel();
            close.run();
        });
        button(g, "reset", "Defaults", x + 14, y, bw + 6, false, () -> {
            Cfg.c = new Cfg();
            System.arraycopy(Colors.hsv(Cfg.c.col), 0, hsvA, 0, 3);
            System.arraycopy(Colors.hsv(Cfg.c.col2), 0, hsvB, 0, 3);
        });
    }

    private void row(Canvas g, String id, String label, String note, float x, float y, float w, boolean on, Runnable flip) {
        float h = note == null ? 16 : 26;
        boolean over = hover(x - 4, y - 3, w + 8, h);
        float k = anim(id + "row", over ? 1f : 0f, 14f);
        if (k > 0.01f) g.fill(x - 4, y - 3, w + 8, h, 7, fade(Colors.alpha(0x16FFFFFF, k)), fade(Colors.alpha(0x0AFFFFFF, k)));
        g.text(label, x + 2, y + 1, fade(on ? TEXT : MUTED));
        toggle(g, id, x + w - 24, y - 1, on, flip);
        hits.add(new Hit(id + "row", x - 4, y - 3, w + 8, h, flip, null, null));
    }

    private void toggle(Canvas g, String id, float x, float y, boolean on, Runnable flip) {
        float w = 22;
        float h = 12;
        float t = anim(id, on ? 1f : 0f, 14f);
        boolean over = hover(x - 2, y - 2, w + 4, h + 4);
        float hl = anim(id + "h", over ? 1f : 0f, 14f);
        int acc = accent();
        int topCol = Colors.mix(0x30FFFFFF, Colors.mix(acc, 0xFFFFFFFF, 0.18f), t);
        int bottomCol = Colors.mix(WELL, acc, t);
        g.fill(x, y, w, h, h / 2f, fade(Colors.mix(topCol, 0xFFFFFFFF, hl * 0.08f)), fade(bottomCol));
        g.ring(x, y, w, h, h / 2f, 1, fade(0x33FFFFFF), fade(0x0DFFFFFF));
        float k = 8 + hl;
        float kx = x + 2 + (w - 4 - k) * t;
        float ky = y + (h - k) / 2f;
        g.shadow(kx, ky + 0.5f, k, k, k / 2f, 2, fade(0x70000000));
        g.fill(kx, ky, k, k, k / 2f, fade(0xFFFFFFFF), fade(0xFFE4E7EE));
        hits.add(new Hit(id, x - 2, y - 2, w + 4, h + 4, flip, null, null));
    }

    private void slider(Canvas g, String id, String label, float x, float y, float w, float v, float lo, float hi, float step, DoubleConsumer set, String shown) {
        g.text(label, x, y, fade(MUTED));
        g.text(shown, x + w - g.width(shown), y, fade(TEXT));
        float ty = y + 11;
        float th = 4;
        float t = Colors.clamp((v - lo) / (hi - lo), 0f, 1f);
        boolean held = id.equals(active);
        float k = anim(id, held || hover(x - 4, ty - 6, w + 8, th + 12) ? 1f : 0f, 16f);
        float at = anim(id + "v", t, 22f);
        int acc = accent();
        g.fill(x, ty, w, th, th / 2f, fade(WELL));
        g.fill(x, ty, Math.max(th, w * at), th, th / 2f, fade(Colors.mix(acc, 0xFFFFFFFF, 0.25f)), fade(acc));
        float s = 8 + k * 2;
        float kx = x + w * at - s / 2f;
        float ky = ty + th / 2f - s / 2f;
        if (k > 0.01f) g.fill(kx - 3 * k, ky - 3 * k, s + 6 * k, s + 6 * k, (s + 6 * k) / 2f, fade(Colors.alpha(acc & 0xFFFFFF | 0x40000000, k)));
        g.shadow(kx, ky + 0.5f, s, s, s / 2f, 2.5f, fade(0x80000000));
        g.fill(kx, ky, s, s, s / 2f, fade(0xFFFFFFFF), fade(0xFFE4E7EE));
        Drag d = (px, py) -> set.accept(snap(lo + (hi - lo) * Colors.clamp((float) ((px - x) / w), 0f, 1f), step, lo, hi));
        hits.add(new Hit(id, x - 4, ty - 7, w + 8, th + 14, null, d, a -> set.accept(snap(v + (float) Math.signum(a) * step * 5f, step, lo, hi))));
    }

    private void segmented(Canvas g, String id, float x, float y, float w, float h, String[] items, int sel, IntConsumer pick) {
        g.fill(x, y, w, h, h / 2f, fade(WELL));
        g.ring(x, y, w, h, h / 2f, 1, fade(EDGE), fade(0x0AFFFFFF));
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
        int acc = accent();
        g.shadow(px + 2, y + 2, pw - 4, h - 4, (h - 4) / 2f, 3, fade(0x60000000));
        g.fill(px + 2, y + 2, pw - 4, h - 4, (h - 4) / 2f, fade(0x36FFFFFF), fade(0x1CFFFFFF));
        g.ring(px + 2, y + 2, pw - 4, h - 4, (h - 4) / 2f, 1, fade(Colors.alpha(acc, 0.75f)), fade(Colors.alpha(acc, 0.25f)));
        for (int i = 0; i < n; i++) {
            float ix = edge[i];
            float iw = edge[i + 1] - ix;
            boolean over = hover(ix, y, iw, h);
            float k = anim(id + i, over ? 1f : 0f, 14f);
            int col = i == sel ? TEXT : Colors.mix(MUTED, TEXT, k * 0.6f);
            g.text(items[i], ix + (iw - g.width(items[i])) / 2f, y + (h - 8) / 2f, fade(col));
            int idx = i;
            hits.add(new Hit(id + i, ix, y, iw, h, () -> pick.accept(idx), null, a -> pick.accept(Math.floorMod(sel - (int) Math.signum(a), n))));
        }
    }

    private void button(Canvas g, String id, String label, float x, float y, float w, boolean main, Runnable run) {
        float h = 18;
        boolean over = hover(x, y, w, h);
        float k = anim(id, over ? 1f : 0f, 14f);
        int acc = accent();
        g.shadow(x, y + 1, w, h, 7, 5, fade(0x70000000));
        if (main) {
            g.fill(x, y, w, h, 7, fade(Colors.mix(Colors.mix(acc, 0xFFFFFFFF, 0.22f), 0xFFFFFFFF, k * 0.12f)), fade(Colors.mix(acc, 0xFFFFFFFF, k * 0.08f)));
            g.ring(x, y, w, h, 7, 1, fade(0x55FFFFFF), fade(0x14FFFFFF));
        } else {
            g.fill(x, y, w, h, 7, fade(Colors.mix(0x26FFFFFF, 0x3DFFFFFF, k)), fade(Colors.mix(0x12FFFFFF, 0x22FFFFFF, k)));
            g.ring(x, y, w, h, 7, 1, fade(0x2EFFFFFF), fade(0x0AFFFFFF));
        }
        int tc = main && Colors.luma(acc) > 0.62f ? 0xFF15161B : TEXT;
        g.text(label, x + (w - g.width(label)) / 2f, y + 5, fade(tc));
        hits.add(new Hit(id, x, y, w, h, run, null, null));
    }

    private void picker(Canvas g, String id, IntSupplier get, IntConsumer set, float[] hsv, float x, float y, float w, float h) {
        int col = get.getAsInt();
        float sw = Math.round(w * 0.52f);
        int hueCol = 0xFF000000 | Colors.fromHsv(hsv[0], 1f, 1f);
        g.shadow(x, y + 1, sw, h, 7, 5, fade(0x70000000));
        g.sv(x, y, sw, h, 7, fade(hueCol));
        g.ring(x, y, sw, h, 7, 1, fade(0x33FFFFFF), fade(0x14FFFFFF));
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
        g.shadow(rx, y + 1, rw, 12, 6, 3, fade(0x60000000));
        g.hue(rx, y, rw, 12, 6, fade(0xFFFFFFFF));
        g.ring(rx, y, rw, 12, 6, 1, fade(0x33FFFFFF), fade(0x14FFFFFF));
        float hx = rx + hsv[0] * rw;
        g.shadow(hx - 3.5f, y - 2, 7, 16, 3.5f, 2, fade(0x90000000));
        g.fill(hx - 3.5f, y - 2, 7, 16, 3.5f, fade(0xFFFFFFFF), fade(0xFFE4E7EE));
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
        float fw = rw - sq - 6;
        boolean focus = id.equals(typing) && active == null;
        float k = anim(id + "hex", focus ? 1f : hover(rx, fy, fw, sq) ? 0.4f : 0f, 14f);
        g.fill(rx, fy, fw, sq, 7, fade(WELL), fade(0x30000000));
        g.ring(rx, fy, fw, sq, 7, 1, fade(Colors.mix(EDGE, accent(), k)), fade(Colors.mix(0x0AFFFFFF, accent(), k * 0.5f)));
        String shown = focus ? "#" + hex : Colors.hex(col);
        g.text(shown, rx + 7, fy + 5, fade(focus && !valid(hex) ? 0xFFFF8080 : TEXT));
        if (focus && (clock % 1f) < 0.55f) {
            float cx = rx + 7 + g.width(shown) + 1;
            g.fill(cx, fy + 4, 1, 10, 0, fade(TEXT));
        }
        hits.add(new Hit(id + "hexf", rx, fy, fw, sq, () -> {
            typing = id;
            hex = Colors.hex(get.getAsInt()).substring(1);
        }, null, null));
        float sx = rx + rw - sq;
        g.shadow(sx, fy + 1, sq, sq, 7, 4, fade(Colors.alpha(0xFF000000 | col, 0.45f)));
        g.fill(sx, fy, sq, sq, 7, fade(0xFF000000 | Colors.mix(0xFF000000 | col, 0xFFFFFFFF, 0.18f)), fade(0xFF000000 | col));
        g.ring(sx, fy, sq, sq, 7, 1, fade(0x55FFFFFF), fade(0x14FFFFFF));
    }

    private void commitHex() {
        if (typing == null) return;
        String id = typing;
        typing = null;
        if (valid(hex)) apply(id, hex);
    }

    private void apply(String id, String s) {
        boolean a = id.equals("a");
        int col = Colors.parse(s, a ? Cfg.c.col : Cfg.c.col2);
        if (a) Cfg.c.col = col;
        else Cfg.c.col2 = col;
        System.arraycopy(Colors.hsv(col), 0, a ? hsvA : hsvB, 0, 3);
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
        if (action == TAB) {
            tab = (tab + 1) % TABS.length;
            return true;
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
        return Colors.luma(b) < 0.22f ? 0xFF000000 | Colors.mix(col, 0xFFFFFFFF, 0.4f) : col;
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
