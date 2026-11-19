package com.zyroofficial.hud;

import com.zyroofficial.config.ZyroConfig;
import com.zyroofficial.util.Colors;
import com.zyroofficial.util.MoneyFormat;
import com.zyroofficial.util.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayDeque;
import java.util.Deque;

/** Queue of popup alerts ("Name paid you $5B") with animations, sparkles and sound. */
public final class PopupManager {
	private static final Deque<Popup> QUEUE = new ArrayDeque<>();
	private static Popup active;
	private static int pendingSounds;
	private static long nextSoundAt;

	private PopupManager() {
	}

	private static final class Popup {
		String title;
		String valueText;
		double value;
		boolean counts;
		long start;
		long duration;
		int valueColor;
	}

	public static synchronized void showPayment(String name, double amount) {
		Popup popup = new Popup();
		ZyroConfig config = ZyroConfig.get();
		popup.title = name == null || name.isBlank() ? "Payment received" : name + " paid you";
		popup.value = amount;
		popup.counts = config.popupCountUp;
		popup.valueColor = Colors.parse(config.popupAmountColor, 0xFF7CFF3A);
		popup.duration = (long) (config.popupDurationSec * 1000);
		enqueue(popup, true);
	}

	/** A popup with free text, used for auction results. */
	public static synchronized void showText(String title, String value, int color, long durationMs, boolean sound) {
		Popup popup = new Popup();
		popup.title = title;
		popup.valueText = value;
		popup.valueColor = color;
		popup.duration = durationMs;
		enqueue(popup, sound);
	}

	/** Demo popup for the "Test" button. */
	public static void test() {
		showPayment("Zyroofficial", 5_000_000_000D);
	}

	private static void enqueue(Popup popup, boolean sound) {
		if (QUEUE.size() >= 6) {
			QUEUE.pollFirst();
		}
		QUEUE.addLast(popup);
		if (sound) {
			playConfiguredSound();
		}
	}

	public static void playConfiguredSound() {
		ZyroConfig config = ZyroConfig.get();
		if (!config.soundEnabled) {
			return;
		}
		pendingSounds = config.soundRepeat;
		nextSoundAt = 0;
	}

	private static void runSounds(long now) {
		if (pendingSounds <= 0 || now < nextSoundAt) {
			return;
		}
		ZyroConfig config = ZyroConfig.get();
		Platform.playSound(config.soundIndex, config.soundVolume / 100f, config.soundPitch / 10f);
		pendingSounds--;
		nextSoundAt = now + 160;
	}

	public static synchronized void render(GuiGraphicsExtractor g, int screenWidth) {
		long now = System.currentTimeMillis();
		runSounds(now);
		if (active != null && now - active.start >= active.duration) {
			active = null;
		}
		if (active == null) {
			active = QUEUE.pollFirst();
			if (active != null) {
				active.start = now;
			}
		}
		if (active == null) {
			return;
		}

		ZyroConfig config = ZyroConfig.get();
		Font font = Minecraft.getInstance().font;
		long elapsed = now - active.start;
		double t = Colors.clamp01((double) elapsed / active.duration);

		String valueText = active.valueText;
		if (valueText == null) {
			double shown = active.counts ? active.value * easeOut(Colors.clamp01(elapsed / 500.0)) : active.value;
			valueText = (config.popupPlus ? "+" : "") + MoneyFormat.compact(shown);
		}

		int w = Math.max(130, Math.max(font.width(active.title), font.width(valueText)) + 28);
		int h = 36;
		int cx = screenWidth / 2 + config.popupX;
		int x = cx - w / 2;
		int y = config.popupY;

		double inT = Colors.clamp01(elapsed / 220.0);
		double outT = Colors.clamp01((active.duration - elapsed) / 260.0);
		double alpha = Math.min(inT, outT);
		double scale = 1;
		switch (config.popupAnimation) {
			case POP:
				scale = 0.6 + 0.4 * easeOutBack(inT);
				alpha = outT;
				break;
			case SLIDE:
				y = (int) (y - (1 - easeOut(Math.min(inT, outT))) * (y + h + 4));
				alpha = 1;
				break;
			case FADE:
			default:
				break;
		}
		if (alpha <= 0.02) {
			return;
		}

		int bw = (int) Math.round(w * scale);
		int bh = (int) Math.round(h * scale);
		int bx = cx - bw / 2;
		int by = y + (h - bh) / 2;
		double panel = config.popupOpacity / 100.0;
		int bg = Colors.withAlpha(Colors.parse(config.popupBgColor, 0xFF140C28), panel * alpha);

		if (config.popupGlow) {
			int glow = Colors.parse(config.popupBorderColor, 0xFFFFD700);
			for (int i = 4; i >= 1; i--) {
				g.fill(bx - i, by - i, bx + bw + i, by + bh + i, Colors.withAlpha(glow, 0.06 * alpha));
			}
		}

		g.fill(bx, by, bx + bw, by + bh, bg);
		drawBorder(g, config, bx, by, bw, bh, alpha, now);

		if (scale >= 0.95) {
			int titleColor = Colors.withAlpha(Colors.parse(config.popupNameColor, 0xFFFFA500), alpha);
			int valueColor = Colors.withAlpha(active.valueColor, alpha);
			g.text(font, active.title, cx - font.width(active.title) / 2, by + 7, titleColor, true);
			g.text(font, valueText, cx - font.width(valueText) / 2, by + 19, valueColor, true);

			if (config.popupTimerBar) {
				int barW = (int) ((bw - 8) * (1 - t));
				g.fill(bx + 4, by + bh - 4, bx + 4 + barW, by + bh - 2,
						Colors.withAlpha(Colors.parse(config.popupTimerColor, 0xFFFFD700), alpha));
			}
			if (config.popupSparkles) {
				drawSparkles(g, config, bx, by, bw, bh, now, alpha);
			}
		}
	}

	private static void drawBorder(GuiGraphicsExtractor g, ZyroConfig config, int x, int y, int w, int h, double alpha, long now) {
		int thick = config.popupBorderWidth;
		if (!config.popupRainbow) {
			Gfx.outline(g, x, y, w, h, thick, Colors.withAlpha(Colors.parse(config.popupBorderColor, 0xFFFFD700), alpha));
			return;
		}
		double shift = (now % 3000) / 3000.0;
		int perimeter = 2 * (w + h);
		for (int i = 0; i < w; i++) {
			int c = Colors.withAlpha(Colors.hsv(shift + (double) i / perimeter, 0.8, 1.0), alpha);
			g.fill(x + i, y, x + i + 1, y + thick, c);
			int c2 = Colors.withAlpha(Colors.hsv(shift + (double) (w + h + (w - i)) / perimeter, 0.8, 1.0), alpha);
			g.fill(x + i, y + h - thick, x + i + 1, y + h, c2);
		}
		for (int j = 0; j < h; j++) {
			int c = Colors.withAlpha(Colors.hsv(shift + (double) (w + j) / perimeter, 0.8, 1.0), alpha);
			g.fill(x + w - thick, y + j, x + w, y + j + 1, c);
			int c2 = Colors.withAlpha(Colors.hsv(shift + (double) (2 * w + h + (h - j)) / perimeter, 0.8, 1.0), alpha);
			g.fill(x, y + j, x + thick, y + j + 1, c2);
		}
	}

	private static void drawSparkles(GuiGraphicsExtractor g, ZyroConfig config, int x, int y, int w, int h, long now, double alpha) {
		int count = config.popupSparkleCount;
		for (int i = 0; i < count; i++) {
			long seed = i * 7919L + 13;
			double phase = ((now + seed * 97) % 1200) / 1200.0;
			double twinkle = Math.sin(phase * Math.PI);
			if (twinkle <= 0.1) {
				continue;
			}
			int px = x + (int) ((seed * 37) % Math.max(1, w));
			int py = y + (int) ((seed * 53) % Math.max(1, h));
			int size = twinkle > 0.7 ? 2 : 1;
			g.fill(px, py, px + size, py + size, Colors.withAlpha(0xFFFFFFFF, twinkle * alpha));
		}
	}

	private static double easeOut(double t) {
		return 1 - (1 - t) * (1 - t);
	}

	private static double easeOutBack(double t) {
		double c1 = 1.70158;
		double c3 = c1 + 1;
		double u = t - 1;
		return 1 + c3 * u * u * u + c1 * u * u;
	}
}
