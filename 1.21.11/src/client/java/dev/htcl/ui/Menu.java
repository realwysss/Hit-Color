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
    private static final int INK = 0xFF22201C;
    private static final int SOFT = 0xFF8A8478;
    private static final int WELL = 0x14000000;
    private static final int EDGE = 0x2A000000;
    private static final int PAPER_TOP = 0xFFF8F3E9;
    private static final int PAPER_BOT = 0xFFEAE3D4;
    private static final int BASE_TOP = 0xFF9AA0AC;
    private static final int BASE_BOT = 0xFF767B87;
    private static final int ARMOR = 0xFF3FD0C8;

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
    private String typing;
    private String hex = "";
    private float open;
    private long last;
    private float dt;
    private float clock;
    private float hitAt;
    private int sig;
    private double mx;
    private double my;

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
        open = ease(open, 1f, 8f);
        Cfg c = Cfg.c;
        int now2 = java.util.Objects.hash(c.col, c.col2, c.str, c.grad, c.ar, c.fade, c.on);
        if (now2 != sig) {
            if (sig != 0) hitAt = clock;
            sig = now2;
        }

        float dw = Math.min(width - 24, 640);
        float dh = Math.min(height - 24, 132);
        float dx = (width - dw) / 2f;
        float dy = height - 12 - dh + (1f - open) * 36f;

        g.fill(0, dy - 60, width, height - dy + 60, 0, fade(0x00000000), fade(0x66000000));

        stage(g, c, width, dy);
        title(g, c);

        g.shadow(dx, dy + 8, dw, dh, 18, 30, fade(0x80000000));
        g.fill(dx, dy, dw, dh, 16, fade(PAPER_TOP), fade(PAPER_BOT));
        g.ring(dx, dy, dw, dh, 16, 1, fade(0x55FFFFFF), fade(0x22000000));

        float pad = 16;
        float iw = dw - pad * 2;
        float gap = 16;
        float w1 = (iw - gap * 2) * 0.35f;
        float w2 = (iw - gap * 2) * 0.33f;
        float w3 = iw - gap * 2 - w1 - w2;
        float x1 = dx + pad;
        float x2 = x1 + w1 + gap;
        float x3 = x2 + w2 + gap;
        float top = dy + 28;
        float ph = Math.max(34, Math.min(46, dh - 88));

        g.fill(x2 - gap / 2f, dy + 14, 1, dh - 60, 0, fade(0x16000000));
        g.fill(x3 - gap / 2f, dy + 14, 1, dh - 60, 0, fade(0x16000000));

        g.text("COLOR", x1, dy + 14, fade(SOFT));
        picker(g, "a", () -> c.col, v -> c.col = v, hsvA, x1, top, w1, ph);
        slider(g, "str", "Opacity", x1, top + ph + 9, w1, c.str, 0f, 100f, 1f, v -> c.str = (int) v, c.str + "%");

        g.text("GRADIENT", x2, dy + 14, fade(SOFT));
        toggle(g, "grad", x2 + w2 - 24, dy + 12, c.grad, () -> c.grad = !c.grad);
        picker(g, "b", () -> c.col2, v -> c.col2 = v, hsvB, x2, top, w2, ph);
        g.text("bottom color up to this", x2, top + ph + 11, fade(SOFT));
        float dim = anim("dim", c.grad ? 0f : 1f, 12f);
        if (dim > 0.01f) {
            g.fill(x2 - 6, top - 4, w2 + 12, ph + 28, 8, fade(Colors.alpha(0xC8F1ECDF, dim)));
            hits.add(new Hit("gradblock", x2 - 6, top - 4, w2 + 12, ph + 28, () -> {
            }, null, null));
        }

        g.text("SHOW ON", x3, dy + 14, fade(SOFT));
        chips(g, c, x3, top, w3);

        float by = dy + dh - 28;
        button(g, "reset", "Defaults", x1, by, 62, false, () -> {
            Cfg.c = new Cfg();
            System.arraycopy(Colors.hsv(Cfg.c.col), 0, hsvA, 0, 3);
            System.arraycopy(Colors.hsv(Cfg.c.col2), 0, hsvB, 0, 3);
        });
        float right = dx + dw - pad;
        button(g, "done", "Done", right - 62, by, 62, true, () -> {
            save();
            close.run();
        });
        button(g, "cancel", "Cancel", right - 62 * 2 - 8, by, 62, false, () -> {
            cancel();
            close.run();
        });
    }

    private void title(Canvas g, Cfg c) {
        float x = 14;
        float y = 14;
        float w = 128;
        float h = 26;
        g.shadow(x, y + 3, w, h, 13, 10, fade(0x55000000));
        g.fill(x, y, w, h, 13, fade(PAPER_TOP), fade(PAPER_BOT));
        g.text("Hit Color", x + 12, y + 9, fade(INK));
        Runnable flip = () -> c.on = !c.on;
        toggle(g, "master", x + w - 32, y + 7, c.on, flip);
    }

    private void stage(Canvas g, Cfg c, int width, float dockTop) {
        float fh = Math.max(48f, Math.min(dockTop - 28f, 230f));
        float u = fh / 32f;
        float cx = width / 2f;
        float fx0 = cx - 8 * u;
        float fy0 = (dockTop - fh) / 2f - 2f;

        float phase = clock - hitAt;
        if (phase > 1.7f) {
            hitAt = clock;
            phase = 0f;
        }
        float k = phase < 0.5f ? (c.fade ? 1f - phase / 0.5f : 1f) : 0f;
        if (!c.on) k = 0f;
        float shake = phase < 0.25f ? (float) Math.sin(phase * 60f) * (0.25f - phase) * 6f * u * 0.2f : 0f;
        float a = Colors.clamp(c.str / 100f, 0f, 1f) * k;
        fx0 += shake;

        boolean over = hover(fx0 - 4, fy0 - 4, 16 * u + 8, fh + 8);
        g.fill(cx - 10 * u, fy0 + fh + 2, 20 * u, u * 1.4f, u * 0.7f, fade(0x44000000));

        float[][] parts = {
            {4, 0, 8, 8}, {4, 8, 8, 12}, {0, 8, 4, 12}, {12, 8, 4, 12}, {4, 20, 4, 12}, {8, 20, 4, 12}
        };
        for (int i = 0; i < parts.length; i++) {
            float[] p = parts[i];
            float px = fx0 + p[0] * u, py = fy0 + p[1] * u, pw = p[2] * u, ph = p[3] * u;
            g.fill(px, py, pw, ph, u * 0.6f, fade(i == 0 ? 0xFFB5AB9C : BASE_TOP), fade(i == 0 ? 0xFF9A9082 : BASE_BOT));
            paint(g, c, px, py, pw, ph, fy0, fh, a, 0xFF000000, 0f);
        }
        float[][] gear = {
            {3.5f, -0.5f, 9f, 5.5f}, {3.5f, 8f, 9f, 12f}, {3.5f, 20f, 5f, 6f}, {7.5f, 20f, 5f, 6f}, {3.5f, 28f, 5f, 4.5f}, {7.5f, 28f, 5f, 4.5f}
        };
        for (float[] p : gear) {
            float px = fx0 + p[0] * u, py = fy0 + p[1] * u, pw = p[2] * u, ph = p[3] * u;
            g.fill(px, py, pw, ph, u * 0.5f, fade(0xFF58E4DC), fade(0xFF2BA8A2));
            g.ring(px, py, pw, ph, u * 0.5f, Math.max(1f, u * 0.3f), fade(0x55FFFFFF), fade(0x22000000));
            if (c.ar) paint(g, c, px, py, pw, ph, fy0, fh, a, 0xFFFFFFFF, 0.8f);
        }

        String hint = "click the figure to replay the hit";
        g.text(hint, 16, 48, fade(0xE6FFFFFF));
        hits.add(new Hit("figure", fx0 - 4, fy0 - 4, 16 * u + 8, fh + 8, () -> hitAt = clock, null, null));
    }

    private void paint(Canvas g, Cfg c, float px, float py, float pw, float ph, float fy0, float fh, float a, int base, float armor) {
        if (a <= 0.001f) return;
        float t1 = 1f - (py - fy0) / fh;
        float t0 = 1f - (py + ph - fy0) / fh;
        int top = c.grad ? Colors.mix(0xFF000000 | c.col, 0xFF000000 | c.col2, t1) : 0xFF000000 | c.col;
        int bot = c.grad ? Colors.mix(0xFF000000 | c.col, 0xFF000000 | c.col2, t0) : 0xFF000000 | c.col;
        float al = armor > 0f ? a * 0.75f : a;
        g.fill(px, py, pw, ph, 3, fade(Colors.alpha(top, al)), fade(Colors.alpha(bot, al)));
    }

    private void chips(Canvas g, Cfg c, float x, float y, float w) {
        String[] names = {"Players", "Mobs", "Yourself", "Armor tint", "Fade out"};
        boolean[] vals = {c.pl, c.mb, c.sf, c.ar, c.fade};
        Runnable[] flips = {() -> c.pl = !c.pl, () -> c.mb = !c.mb, () -> c.sf = !c.sf, () -> c.ar = !c.ar, () -> c.fade = !c.fade};
        float cx = x;
        float cy = y;
        for (int i = 0; i < names.length; i++) {
            float cw = g.width(names[i]) + 18;
            if (cx + cw > x + w + 0.5f && cx > x) {
                cx = x;
                cy += 19;
            }
            chip(g, "chip" + i, names[i], cx, cy, cw, vals[i], flips[i]);
            cx += cw + 6;
        }
    }

    private void chip(Canvas g, String id, String label, float x, float y, float w, boolean on, Runnable flip) {
        float h = 15;
        boolean over = hover(x, y, w, h);
        float t = anim(id, on ? 1f : 0f, 14f);
        float k = anim(id + "h", over ? 1f : 0f, 14f);
        int fillTop = Colors.mix(0x00000000, Colors.mix(INK, 0xFF55524A, 0.5f), t);
        int fillBot = Colors.mix(0x00000000, INK, t);
        g.fill(x, y, w, h, h / 2f, fade(Colors.mix(fillTop, 0x14000000, (1f - t) * k)), fade(Colors.mix(fillBot, 0x14000000, (1f - t) * k)));
        g.ring(x, y, w, h, h / 2f, 1, fade(Colors.mix(0x44000000, INK, t)));
        int tc = Colors.mix(0xFF5E5A50, 0xFFFFFFFF, t);
        g.text(label, x + (w - g.width(label)) / 2f, y + 3.5f, fade(tc));
        hits.add(new Hit(id, x, y, w, h, flip, null, null));
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
        hits.add(new Hit(id, x - 4, ty - 7, w + 8, th + 14, null, d, a -> set.accept(snap(v + (float) Math.signum(a) * step * 5f, step, lo, hi))));
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
        float fw = rw;
        boolean focus = id.equals(typing) && active == null;
        float k = anim(id + "hex", focus ? 1f : hover(rx, fy, fw, sq) ? 0.4f : 0f, 14f);
        g.fill(rx, fy, fw, sq, 7, fade(WELL), fade(0x1E000000));
        g.ring(rx, fy, fw, sq, 7, 1, fade(Colors.mix(EDGE, accent(), k)), fade(Colors.mix(0x14000000, accent(), k * 0.5f)));
        String shown = focus ? hex : Colors.hex(col).substring(1);
        g.fill(rx + 5, fy + 5, 8, 8, 4, fade(0xFF000000 | col));
        g.ring(rx + 5, fy + 5, 8, 8, 4, 1, fade(0x44000000));
        g.text(shown, rx + 17, fy + 5, fade(focus && !valid(hex) ? 0xFFD03030 : INK));
        if (focus && (clock % 1f) < 0.55f) {
            float cx = rx + 17 + g.width(shown) + 1;
            g.fill(cx, fy + 4, 1, 10, 0, fade(INK));
        }
        hits.add(new Hit(id + "hexf", rx, fy, fw, sq, () -> {
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
