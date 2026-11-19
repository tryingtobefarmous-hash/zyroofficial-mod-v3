package com.zyroofficial.hud;

import com.zyroofficial.ZyroofficialClient;
import com.zyroofficial.auction.Auction;
import com.zyroofficial.auction.AuctionManager;
import com.zyroofficial.config.ZyroConfig;
import com.zyroofficial.data.TransactionStore;
import com.zyroofficial.util.Colors;
import com.zyroofficial.util.DurationParser;
import com.zyroofficial.util.MoneyFormat;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Draws the profit box, win streak box, auction board and popups. */
public final class ZyroHud {
	private static final long SESSION_START = System.currentTimeMillis();

	/** True while the HUD editor is open: every box is shown with sample data. */
	public static volatile boolean editing;
	/** Boxes are also shown with sample data until this time (used by the settings screen preview). */
	public static volatile long previewUntil;

	private static double cachedNet;
	private static long cachedAt;
	private static int cachedVersion = -1;
	private static ZyroConfig.ProfitScope cachedScope;

	private ZyroHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(ZyroofficialClient.MOD_ID, "zyro_hud"),
				ZyroHud::extract
		);
	}

	private static boolean preview() {
		boolean inEditor = editing && Minecraft.getInstance().gui.screen() instanceof com.zyroofficial.screen.HudEditorScreen;
		return inEditor || System.currentTimeMillis() < previewUntil;
	}

	private static double profit(ZyroConfig config) {
		long now = System.currentTimeMillis();
		TransactionStore store = TransactionStore.get();
		int version = store.version();
		if (version != cachedVersion || config.profitScope != cachedScope || now - cachedAt > 1000) {
			switch (config.profitScope) {
				case TODAY:
					cachedNet = store.netSince(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
					break;
				case SESSION:
					cachedNet = store.netSince(SESSION_START);
					break;
				case ALL_TIME:
				default:
					cachedNet = store.netProfit();
					break;
			}
			cachedVersion = version;
			cachedScope = config.profitScope;
			cachedAt = now;
		}
		return cachedNet;
	}

	private static String profitTitle(ZyroConfig config) {
		switch (config.profitScope) {
			case TODAY:
				return "Profit of the day";
			case SESSION:
				return "Session profit";
			default:
				return "Total profit";
		}
	}

	private static boolean streakInProfitBox(ZyroConfig config) {
		return config.streakVisible && !config.streakSeparate;
	}

	// ---- bounds, also used by the HUD editor ----

	public static int[] profitBounds(Font font) {
		ZyroConfig config = ZyroConfig.get();
		String amount = MoneyFormat.hud(preview() && !hasData() ? 1_250_000 : profit(config));
		int w = Math.max(font.width(profitTitle(config)), font.width(amount)) + 16;
		int h = 6 + 10 + 12 + 4;
		if (streakInProfitBox(config)) {
			w = Math.max(w, font.width("Win streak: " + config.winStreak) + 16);
			h += 11;
		}
		return new int[] {config.hudX, config.hudY, w, h};
	}

	public static int[] streakBounds(Font font) {
		ZyroConfig config = ZyroConfig.get();
		int w = font.width("Win streak: " + config.winStreak) + 16;
		return new int[] {config.streakX, config.streakY, w, 18};
	}

	public static int[] auctionBounds(Font font) {
		ZyroConfig config = ZyroConfig.get();
		Auction auction = auctionToShow();
		if (auction == null) {
			return new int[] {config.auctionX, config.auctionY, 150, 40};
		}
		List<Auction.Standing> list = auction.standings();
		int rows = Math.min(config.auctionRows, list.size());
		int w = font.width(auctionTitle(auction)) + 16;
		int drawnRows = Math.max(1, rows);
		for (int i = 0; i < rows; i++) {
			w = Math.max(w, font.width(rowText(i, list.get(i))) + 16);
		}
		w = Math.max(w, 130);
		int h = 6 + 10 + 11 + drawnRows * 10 + 4;
		return new int[] {config.auctionX, config.auctionY, w, h};
	}

	private static boolean hasData() {
		return TransactionStore.get().size() > 0;
	}

	// ---- drawing ----

	private static void extract(GuiGraphicsExtractor g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		ZyroConfig config = ZyroConfig.get();
		Font font = mc.font;
		boolean preview = preview();

		if (config.hudMaster || preview) {
			if (config.hudVisible || preview) {
				drawProfit(g, font, config, preview);
			}
			if (config.streakVisible && config.streakSeparate) {
				drawStreak(g, font, config);
			}
			if (config.auctionHudVisible || preview) {
				drawAuction(g, font, config);
			}
		}
		PopupManager.render(g, mc.getWindow().getGuiScaledWidth());
	}

	private static void drawProfit(GuiGraphicsExtractor g, Font font, ZyroConfig config, boolean preview) {
		double net = preview && !hasData() ? 1_250_000 : profit(config);
		int[] b = profitBounds(font);
		int bg = Colors.withAlpha(Colors.parse(config.hudBgColor, 0xFF0B0B14), config.hudOpacity / 100.0);
		int accent = Colors.parse(config.hudAccentColor, 0xFFFFAA00);
		Gfx.box(g, b[0], b[1], b[2], b[3], bg, config.hudBorder, accent, config.hudAccentBar, accent);

		int x = b[0] + 8;
		int y = b[1] + 5;
		g.text(font, profitTitle(config), x, y, Colors.parse(config.hudTitleColor, 0xFFFFC400), true);
		int color = net > 0 ? Colors.parse(config.hudGainColor, 0xFF7CFF3A)
				: net < 0 ? Colors.parse(config.hudLossColor, 0xFFFF4040)
				: Colors.parse(config.hudZeroColor, 0xFFFFFFFF);
		g.text(font, MoneyFormat.hud(net), x, y + 11, color, true);
		if (streakInProfitBox(config)) {
			g.text(font, "Win streak: ", x, y + 23, Colors.parse(config.streakLabelColor, 0xFFAAAAAA), false);
			g.text(font, String.valueOf(config.winStreak), x + font.width("Win streak: "), y + 23,
					Colors.parse(config.streakNumberColor, 0xFFFFD700), true);
		}
	}

	private static void drawStreak(GuiGraphicsExtractor g, Font font, ZyroConfig config) {
		int[] b = streakBounds(font);
		int bg = Colors.withAlpha(Colors.parse(config.streakBgColor, 0xFF0B0B14), config.streakOpacity / 100.0);
		int accent = Colors.parse(config.streakAccentColor, 0xFFFF8A00);
		Gfx.box(g, b[0], b[1], b[2], b[3], bg, config.streakBorder, accent, config.streakAccentBar, accent);
		int x = b[0] + 8;
		g.text(font, "Win streak: ", x, b[1] + 5, Colors.parse(config.streakLabelColor, 0xFFAAAAAA), false);
		g.text(font, String.valueOf(config.winStreak), x + font.width("Win streak: "), b[1] + 5,
				Colors.parse(config.streakNumberColor, 0xFFFFD700), true);
	}

	private static Auction sample;

	private static Auction auctionToShow() {
		ZyroConfig config = ZyroConfig.get();
		Auction real = AuctionManager.get().display(System.currentTimeMillis(), config.auctionResultSeconds);
		if (real != null) {
			return real;
		}
		if (!preview()) {
			return null;
		}
		if (sample == null) {
			sample = new Auction();
			sample.item = "Sample item";
			sample.minBid = 0;
			long now = System.currentTimeMillis();
			sample.startMs = now;
			sample.endMs = now + 5 * 60_000L;
			String[] names = {"Zyro", "Steve", "Alex"};
			double[] amounts = {5_000_000, 2_500_000, 1_000_000};
			for (int i = 0; i < names.length; i++) {
				Auction.Bid bid = new Auction.Bid();
				bid.player = names[i];
				bid.amount = amounts[i];
				bid.time = now + i;
				sample.bids.add(bid);
			}
		}
		sample.endMs = System.currentTimeMillis() + 5 * 60_000L;
		return sample;
	}

	private static String auctionTitle(Auction auction) {
		return "Auction: " + auction.item;
	}

	private static String rowText(int index, Auction.Standing standing) {
		return (index + 1) + ". " + standing.player + "  " + MoneyFormat.compact(standing.score);
	}

	private static void drawAuction(GuiGraphicsExtractor g, Font font, ZyroConfig config) {
		Auction auction = auctionToShow();
		if (auction == null) {
			return;
		}
		int[] b = auctionBounds(font);
		int accent = Colors.parse(config.hudAccentColor, 0xFFFFAA00);
		int bg = Colors.withAlpha(Colors.parse(config.hudBgColor, 0xFF0B0B14), config.auctionHudOpacity / 100.0);
		Gfx.box(g, b[0], b[1], b[2], b[3], bg, true, accent, true, accent);

		int x = b[0] + 8;
		int y = b[1] + 5;
		g.text(font, auctionTitle(auction), x, y, Colors.parse(config.hudTitleColor, 0xFFFFC400), true);

		long now = System.currentTimeMillis();
		if (auction.status == Auction.Status.ENDED) {
			String line = auction.winner == null || auction.winner.isEmpty()
					? "Ended - no winner"
					: "Winner: " + auction.winner + " - " + MoneyFormat.compact(auction.winningAmount);
			g.text(font, line, x, y + 11, 0xFF7CFF3A, true);
		} else {
			long left = auction.endMs - now;
			int timeColor = left < 10_000 ? 0xFFFF4040 : left < 60_000 ? 0xFFFFD700 : 0xFFFFFFFF;
			g.text(font, "Time left: " + DurationParser.format(Math.max(0, left)), x, y + 11, timeColor, true);
		}

		List<Auction.Standing> list = auction.standings();
		int rows = Math.min(config.auctionRows, list.size());
		Auction.Standing leader = auction.leader();
		for (int i = 0; i < rows; i++) {
			Auction.Standing standing = list.get(i);
			int color = standing == leader ? 0xFFFFD700 : 0xFFDDDDDD;
			g.text(font, rowText(i, standing), x, y + 23 + i * 10, color, false);
		}
		if (list.isEmpty()) {
			g.text(font, "No bids yet", x, y + 23, 0xFFAAAAAA, false);
		}
	}
}
