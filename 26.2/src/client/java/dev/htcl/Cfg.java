package dev.htcl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class Cfg {
	public boolean on = true;
	public int col = 0xff2020;
	public int col2 = 0xffb020;
	public int colm = 0xff6020;
	public boolean grad = false;
	public int stops = 2;
	public int bias = 50;
	public int str = 50;
	public int endStr = 0;
	public int curve = 1;
	public int pulses = 0;
	public boolean pl = true;
	public boolean mb = true;
	public boolean sf = true;
	public boolean ar = false;
	public int arStr = 100;

	static final Gson G = new GsonBuilder().setPrettyPrinting().create();
	static final Path P = FabricLoader.getInstance().getConfigDir().resolve("hitcolor.json");
	public static Cfg c = new Cfg();

	public Cfg copy() {
		return G.fromJson(G.toJson(this), Cfg.class);
	}

	public static void ld() {
		try {
			if (Files.exists(P)) {
				Cfg n = G.fromJson(Files.readString(P), Cfg.class);
				if (n != null) c = n;
			}
		} catch (Exception e) {
			c = new Cfg();
		}
		fx();
	}

	public static void fx() {
		c.col &= 0xffffff;
		c.col2 &= 0xffffff;
		c.colm &= 0xffffff;
		c.stops = Math.max(2, Math.min(3, c.stops));
		c.bias = Math.max(10, Math.min(90, c.bias));
		c.str = Math.max(0, Math.min(100, c.str));
		c.endStr = Math.max(0, Math.min(100, c.endStr));
		c.curve = Math.max(0, Math.min(3, c.curve));
		c.pulses = Math.max(0, Math.min(4, c.pulses));
		c.arStr = Math.max(0, Math.min(100, c.arStr));
	}

	public static void sv() {
		fx();
		try {
			Files.writeString(P, G.toJson(c));
		} catch (Exception e) {
		}
	}
}
