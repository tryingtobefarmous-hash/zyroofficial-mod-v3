package com.zyroofficial.util;

/** Color helpers. All colors are 0xAARRGGBB unless stated otherwise. */
public final class Colors {
	private Colors() {
	}

	public static boolean valid(String hex) {
		if (hex == null) {
			return false;
		}
		String s = hex.trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		return s.length() == 6 && s.matches("[0-9A-Fa-f]{6}");
	}

	/** Parses "#RRGGBB" into an opaque 0xFFRRGGBB color, or returns the fallback. */
	public static int parse(String hex, int fallback) {
		if (!valid(hex)) {
			return fallback;
		}
		String s = hex.trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		return 0xFF000000 | Integer.parseInt(s, 16);
	}

	public static String toHex(int color) {
		return String.format("#%06X", color & 0xFFFFFF);
	}

	/** Replaces the alpha of a color. alpha is 0..1. */
	public static int withAlpha(int color, double alpha) {
		int a = (int) Math.round(255 * clamp01(alpha));
		return (a << 24) | (color & 0xFFFFFF);
	}

	/** Multiplies the existing alpha of a color by factor (0..1). */
	public static int fade(int color, double factor) {
		int a = (color >>> 24) & 0xFF;
		int na = (int) Math.round(a * clamp01(factor));
		return (na << 24) | (color & 0xFFFFFF);
	}

	public static int lerp(int a, int b, double t) {
		t = clamp01(t);
		int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	/** Hue 0..1 (wraps), saturation and value 0..1. Returns an opaque color. */
	public static int hsv(double h, double s, double v) {
		h = h - Math.floor(h);
		double scaled = h * 6.0;
		int i = (int) Math.floor(scaled);
		double f = scaled - i;
		double p = v * (1 - s);
		double q = v * (1 - f * s);
		double t = v * (1 - (1 - f) * s);
		double r;
		double g;
		double b;
		switch (i % 6) {
			case 0:
				r = v;
				g = t;
				b = p;
				break;
			case 1:
				r = q;
				g = v;
				b = p;
				break;
			case 2:
				r = p;
				g = v;
				b = t;
				break;
			case 3:
				r = p;
				g = q;
				b = v;
				break;
			case 4:
				r = t;
				g = p;
				b = v;
				break;
			default:
				r = v;
				g = p;
				b = q;
				break;
		}
		return 0xFF000000 | ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
	}

	public static double clamp01(double v) {
		return v < 0 ? 0 : Math.min(v, 1);
	}
}
