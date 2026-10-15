package dev.htcl;

import com.mojang.blaze3d.platform.NativeImage;
import dev.htcl.mixin.OvAcc;
import java.util.IdentityHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public class Hc {
	static boolean act, off, arm, grad;
	static float feet, hgt;
	static int fi;
	static long sig = -1;
	static DynamicTexture last;
	static final IdentityHashMap<RenderType, Boolean> AR = new IdentityHashMap<>();
	public static int hits;

	static boolean armor(RenderType rt) {
		Boolean b = AR.get(rt);
		if (b == null) {
			b = rt.toString().toLowerCase().contains("armor");
			AR.put(rt, b);
		}
		return b;
	}

	public static float st(float p) {
		Cfg c = Cfg.c;
		float x = switch (c.curve) {
			case 0 -> 0f;
			case 1 -> p;
			case 2 -> p * p;
			default -> 1f - (1f - p) * (1f - p);
		};
		float v = c.str + (c.endStr - c.str) * x;
		if (c.pulses > 0) v *= 0.5f + 0.5f * (float) Math.cos(6.2831855f * c.pulses * p);
		return Math.max(0f, Math.min(100f, v)) / 100f;
	}

	public static int gc(float t) {
		Cfg c = Cfg.c;
		if (!c.grad) return c.col;
		if (c.stops < 3) return mix(c.col, c.col2, t);
		float b = c.bias / 100f;
		return t < b ? mix(c.col, c.colm, t / b) : mix(c.colm, c.col2, (t - b) / (1f - b));
	}

	static int mix(int a, int b, float t) {
		int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
		int g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
		int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
		return r << 16 | g << 8 | bl;
	}

	static void sync() {
		Cfg c = Cfg.c;
		DynamicTexture t = ((OvAcc) Minecraft.getInstance().gameRenderer.overlayTexture()).tex();
		long s = java.util.Objects.hash(c.col, c.col2, c.colm, c.grad, c.stops, c.bias, c.str, c.endStr, c.curve, c.pulses);
		if (s == sig && t == last) return;
		NativeImage im = t.getPixels();
		if (im == null) return;
		for (int r = 0; r < 8; r++) {
			int cc = gc(r / 7f);
			for (int u = 0; u < 16; u++) {
				float a = 1f - st(u / 15f);
				im.setPixel(u, r, Math.round(a * 255f) << 24 | cc);
			}
		}
		t.upload();
		sig = s;
		last = t;
	}

	public static void begin(SubmitNodeStorage.ModelSubmit<?> ms, RenderType rt) {
		act = false;
		Cfg c = Cfg.c;
		if (!c.on) return;
		if (!(ms.state() instanceof LivingEntityRenderState s) || !s.hasRedOverlay) return;
		sync();
		HcSt h = (HcSt) s;
		boolean pv = h.hcSelf() && Minecraft.getInstance().screen instanceof dev.htcl.ui.Panel;
		boolean en = pv || (h.hcSelf() ? c.sf : s.entityType == EntityType.PLAYER ? c.pl : c.mb);
		boolean a = armor(rt);
		off = !en;
		arm = a && c.ar && en;
		if (a && !arm) return;
		act = true;
		grad = c.grad;
		fi = Math.max(0, Math.min(15, Math.round((1f - h.hcP()) * 15f)));
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		feet = (float) (s.y - cam.y);
		hgt = Math.max(0.1f, s.boundingBoxHeight);
		hits++;
	}

	public static void end() {
		act = false;
	}

	static float t(float y) {
		return Math.max(0f, Math.min(1f, (y - feet) / hgt));
	}

	public static int ov(int o) {
		if (!act || arm || (o >>> 16) >= 8) return o;
		if (off) return OverlayTexture.NO_OVERLAY;
		return OverlayTexture.pack(fi, 3);
	}

	public static int ovv(int o, float y) {
		if (!act || arm || off || !grad || (o >>> 16) >= 8) return o;
		return (o & 0xffff) | Math.round(t(y) * 7f) << 16;
	}

	static int tint(int k, float t) {
		Cfg c = Cfg.c;
		int cc = gc(t);
		float f = st(fi / 15f) * c.arStr / 100f;
		int r = Math.round(255 + ((cc >> 16 & 255) - 255) * f);
		int g = Math.round(255 + ((cc >> 8 & 255) - 255) * f);
		int b = Math.round(255 + ((cc & 255) - 255) * f);
		int ka = k >>> 24, kr = (k >> 16 & 255) * r / 255, kg = (k >> 8 & 255) * g / 255, kb = (k & 255) * b / 255;
		return ka << 24 | kr << 16 | kg << 8 | kb;
	}

	public static int tc(int k) {
		if (!act || !arm || grad) return k;
		return tint(k, 0f);
	}

	public static int col(int k, float y) {
		if (!act || !arm || !grad) return k;
		return tint(k, t(y));
	}
}
