package com.zyroofficial.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small drawing helpers built only on rectangle fills. */
public final class Gfx {
	private Gfx() {
	}

	/** A rectangle outline of the given thickness drawn inside the bounds. */
	public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int thick, int color) {
		if (w <= 0 || h <= 0 || thick <= 0) {
			return;
		}
		g.fill(x, y, x + w, y + thick, color);
		g.fill(x, y + h - thick, x + w, y + h, color);
		g.fill(x, y + thick, x + thick, y + h - thick, color);
		g.fill(x + w - thick, y + thick, x + w, y + h - thick, color);
	}

	/** Background, optional border and optional accent bar on the left. */
	public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int background,
			boolean border, int borderColor, boolean accentBar, int accentColor) {
		g.fill(x, y, x + w, y + h, background);
		if (border) {
			outline(g, x, y, w, h, 1, borderColor);
		}
		if (accentBar) {
			g.fill(x, y, x + 2, y + h, accentColor);
		}
	}

	/** Vertical gradient made of thin strips. */
	public static void gradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom) {
		int steps = Math.max(1, Math.min(h, 48));
		for (int i = 0; i < steps; i++) {
			int y1 = y + h * i / steps;
			int y2 = y + h * (i + 1) / steps;
			double t = steps == 1 ? 0 : (double) i / (steps - 1);
			g.fill(x, y1, x + w, y2, com.zyroofficial.util.Colors.lerp(top, bottom, t));
		}
	}
}
