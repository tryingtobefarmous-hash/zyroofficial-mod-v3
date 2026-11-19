package com.zyroofficial.screen;

import com.zyroofficial.ZyroofficialClient;
import com.zyroofficial.chat.ChatDetectionConfig;
import com.zyroofficial.config.ZyroConfig;
import com.zyroofficial.hud.Gfx;
import com.zyroofficial.hud.PopupManager;
import com.zyroofficial.hud.ZyroHud;
import com.zyroofficial.util.Colors;
import com.zyroofficial.util.MoneyFormat;
import com.zyroofficial.util.Platform;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** The main settings menu: tabs for every setting of the mod. */
public class ZyroScreen extends Screen {
	private static final String[] TABS = {"General", "Chat", "Filter", "Profit", "Streak", "Popup", "Sounds", "Auction"};
	private static final int ROW = 24;

	private interface Cell {
		void add(int x, int y, int w);
	}

	/** Text drawn after the widgets: a label, an optional color swatch. */
	private static final class Label {
		int x;
		int y;
		int maxWidth;
		boolean centered;
		Supplier<String> text;
		int color = Ui.TEXT;
		Supplier<String> swatchHex;
	}

	private final int tab;
	private int page;
	private int pages = 1;
	private boolean resetArmed;

	private final List<Label> labels = new ArrayList<>();
	private int left;
	private int contentW;
	private int bottomLimit;

	public ZyroScreen() {
		this(0, 0);
	}

	public ZyroScreen(int tab, int page) {
		super(Component.literal(ZyroofficialClient.MOD_NAME));
		this.tab = Math.max(0, Math.min(TABS.length - 1, tab));
		this.page = Math.max(0, page);
	}

	private static ZyroConfig c() {
		return ZyroConfig.get();
	}

	private static void save() {
		ZyroConfig.save();
		ChatDetectionConfig.save();
	}

	// ---------------------------------------------------------------------------------------------
	// Layout
	// ---------------------------------------------------------------------------------------------

	@Override
	protected void init() {
		this.labels.clear();
		this.contentW = Math.min(this.width - 20, 430);
		this.left = (this.width - this.contentW) / 2;

		int gap = 2;
		int tabW = (this.contentW - gap * (TABS.length - 1)) / TABS.length;
		for (int i = 0; i < TABS.length; i++) {
			final int index = i;
			Button button = Button.builder(Component.literal(TABS[i]),
							b -> this.minecraft.gui.setScreen(new ZyroScreen(index, 0)))
					.bounds(this.left + i * (tabW + gap), 31, tabW, 20).build();
			button.active = i != this.tab;
			this.addRenderableWidget(button);
		}

		List<Cell> cells = cellsFor(this.tab);
		int top = 58;
		this.bottomLimit = this.height - 42;
		int rowsPerPage = Math.max(1, (this.bottomLimit - top) / ROW);
		int perPage = rowsPerPage * 2;
		this.pages = Math.max(1, (cells.size() + perPage - 1) / perPage);
		this.page = Math.min(this.page, this.pages - 1);

		int colW = (this.contentW - 6) / 2;
		int first = this.page * perPage;
		for (int i = 0; i < perPage && first + i < cells.size(); i++) {
			int col = i % 2;
			int row = i / 2;
			cells.get(first + i).add(this.left + col * (colW + 6), top + row * ROW, colW);
		}

		int by = this.height - 24;
		int third = (this.contentW - 8) / 3;
		Button prev = this.addRenderableWidget(Button.builder(Component.literal("< Page"),
						b -> this.minecraft.gui.setScreen(new ZyroScreen(this.tab, this.page - 1)))
				.bounds(this.left, by, third, 20).build());
		prev.active = this.page > 0;
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(this.left + third + 4, by, third, 20).build());
		Button next = this.addRenderableWidget(Button.builder(Component.literal("Page >"),
						b -> this.minecraft.gui.setScreen(new ZyroScreen(this.tab, this.page + 1)))
				.bounds(this.left + (third + 4) * 2, by, third, 20).build());
		next.active = this.page < this.pages - 1;
	}

	// ---------------------------------------------------------------------------------------------
	// Cell builders
	// ---------------------------------------------------------------------------------------------

	private static String onOff(String label, boolean value) {
		return label + ": " + (value ? "ON" : "OFF");
	}

	private Cell toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
		return (x, y, w) -> this.addRenderableWidget(Button.builder(Component.literal(onOff(label, get.getAsBoolean())), button -> {
					set.accept(!get.getAsBoolean());
					save();
					button.setMessage(Component.literal(onOff(label, get.getAsBoolean())));
				})
				.bounds(x, y, w, 20).build());
	}

	private Cell action(String label, Runnable run) {
		return (x, y, w) -> this.addRenderableWidget(Button.builder(Component.literal(label), button -> run.run())
				.bounds(x, y, w, 20).build());
	}

	private Cell cycle(String label, Supplier<String> value, Runnable next) {
		return (x, y, w) -> this.addRenderableWidget(Button.builder(Component.literal(label + ": " + value.get()), button -> {
					next.run();
					save();
					button.setMessage(Component.literal(label + ": " + value.get()));
				})
				.bounds(x, y, w, 20).build());
	}

	private Cell stepper(String label, IntSupplier get, IntConsumer set, int min, int max, int step, IntFunction<String> format) {
		return (x, y, w) -> {
			Label text = new Label();
			text.x = x + w / 2;
			text.y = y + 6;
			text.centered = true;
			text.maxWidth = w - 44;
			text.text = () -> label + ": " + format.apply(get.getAsInt());
			this.labels.add(text);

			this.addRenderableWidget(Button.builder(Component.literal("-"), button -> {
						set.accept(Math.max(min, get.getAsInt() - step));
						save();
					})
					.bounds(x, y, 20, 20).build());
			this.addRenderableWidget(Button.builder(Component.literal("+"), button -> {
						set.accept(Math.min(max, get.getAsInt() + step));
						save();
					})
					.bounds(x + w - 20, y, 20, 20).build());
		};
	}

	private Cell colorField(String label, Supplier<String> get, Consumer<String> set) {
		return (x, y, w) -> {
			Label text = new Label();
			text.x = x + 14;
			text.y = y + 6;
			text.maxWidth = w - 90;
			text.text = () -> label;
			text.swatchHex = get;
			this.labels.add(text);

			EditBox box = new EditBox(this.font, x + w - 64, y, 64, 20, Component.literal(label));
			box.setMaxLength(7);
			box.setValue(get.get());
			box.setResponder(value -> {
				if (Colors.valid(value) && !value.equals(get.get())) {
					set.accept(value.toUpperCase(java.util.Locale.ROOT));
					save();
				}
			});
			this.addRenderableWidget(box);
		};
	}

	private static double nextAmount(double current, boolean wrapToZero) {
		for (double step : ZyroConfig.AMOUNT_STEPS) {
			if (step > current + 0.5) {
				return step;
			}
		}
		return wrapToZero ? 0 : ZyroConfig.AMOUNT_STEPS[0];
	}

	// ---------------------------------------------------------------------------------------------
	// Tabs
	// ---------------------------------------------------------------------------------------------

	private List<Cell> cellsFor(int index) {
		switch (index) {
			case 0:
				return general();
			case 1:
				return chat();
			case 2:
				return filter();
			case 3:
				return profit();
			case 4:
				return streak();
			case 5:
				return popup();
			case 6:
				return sounds();
			default:
				return auction();
		}
	}

	private List<Cell> general() {
		List<Cell> list = new ArrayList<>();
		list.add(cycle("Theme", () -> c().themeName, () -> {
			ZyroConfig.Theme[] themes = ZyroConfig.THEMES;
			int current = 0;
			for (int i = 0; i < themes.length; i++) {
				if (themes[i].name.equals(c().themeName)) {
					current = i;
				}
			}
			ZyroConfig.Theme next = themes[(current + 1) % themes.length];
			c().themeName = next.name;
			c().themeTop = next.top;
			c().themeBottom = next.bottom;
			c().accent = next.accent;
		}));
		list.add(toggle("Rainbow title", () -> c().rainbowHeader, v -> c().rainbowHeader = v));
		list.add(toggle("HUD (master)", () -> c().hudMaster, v -> c().hudMaster = v));
		list.add(cycle("Ignore below", () -> c().minTransactionAmount <= 0 ? "nothing" : MoneyFormat.compact(c().minTransactionAmount),
				() -> c().minTransactionAmount = nextAmount(c().minTransactionAmount, true)));
		list.add(stepper("Pay tax", () -> (int) Math.round(c().payTaxPercent), v -> c().payTaxPercent = v, 0, 50, 1, v -> v + "%"));
		list.add(toggle("Count tax", () -> c().countTax, v -> c().countTax = v));
		list.add(toggle("Show tax in chat", () -> c().showTaxInChat, v -> c().showTaxInChat = v));
		list.add(action("Auction", () -> this.minecraft.gui.setScreen(new AuctionScreen(this))));
		list.add(action("Money statistics", () -> this.minecraft.gui.setScreen(new MoneyStatsScreen(this))));
		list.add(action("Add entry", () -> this.minecraft.gui.setScreen(new AddTransactionScreen(this))));
		list.add(action("History", () -> this.minecraft.gui.setScreen(new HistoryScreen(this))));
		list.add(action("Chat rules", () -> this.minecraft.gui.setScreen(new ChatDetectionScreen(this))));
		list.add(action("Recent messages", () -> this.minecraft.gui.setScreen(new RecentMessagesScreen(this))));
		list.add(action("Move HUDs", () -> this.minecraft.gui.setScreen(new HudEditorScreen(this))));
		list.add((x, y, w) -> this.addRenderableWidget(Button.builder(Component.literal("Reset all settings"), button -> {
					if (!this.resetArmed) {
						this.resetArmed = true;
						button.setMessage(Component.literal("Click again to confirm"));
						return;
					}
					ZyroConfig.reset();
					this.minecraft.gui.setScreen(new ZyroScreen(this.tab, 0));
				})
				.bounds(x, y, w, 20).build()));
		return list;
	}

	private List<Cell> chat() {
		List<Cell> list = new ArrayList<>();
		list.add(toggle("Auto detect", () -> ChatDetectionConfig.get().enabled, v -> ChatDetectionConfig.get().enabled = v));
		list.add(toggle("Duplicate filter", () -> ChatDetectionConfig.get().duplicateFilterEnabled,
				v -> ChatDetectionConfig.get().duplicateFilterEnabled = v));
		list.add(toggle("Count received", () -> ChatDetectionConfig.get().detectReceived,
				v -> ChatDetectionConfig.get().detectReceived = v));
		list.add(toggle("Count sent", () -> ChatDetectionConfig.get().detectSent,
				v -> ChatDetectionConfig.get().detectSent = v));
		list.add(toggle("Recolor chat", () -> c().chatColors, v -> c().chatColors = v));
		list.add(toggle("Bold names", () -> c().boldNames, v -> c().boldNames = v));
		list.add(toggle("Bold amounts", () -> c().boldAmounts, v -> c().boldAmounts = v));
		list.add(colorField("Name color", () -> c().chatNameColor, v -> c().chatNameColor = v));
		list.add(colorField("Received color", () -> c().chatReceivedColor, v -> c().chatReceivedColor = v));
		list.add(colorField("Sent color", () -> c().chatSentColor, v -> c().chatSentColor = v));
		list.add(colorField("Tax color", () -> c().chatTaxColor, v -> c().chatTaxColor = v));
		list.add(action("Edit chat rules", () -> this.minecraft.gui.setScreen(new ChatDetectionScreen(this))));
		list.add(action("Recent messages", () -> this.minecraft.gui.setScreen(new RecentMessagesScreen(this))));
		return list;
	}

	private List<Cell> filter() {
		List<Cell> list = new ArrayList<>();
		list.add(toggle("Hide small payments", () -> c().hideSmall, v -> c().hideSmall = v));
		list.add(cycle("Hide below", () -> MoneyFormat.compact(c().hideBelowAmount),
				() -> c().hideBelowAmount = nextAmount(c().hideBelowAmount, false)));
		list.add(toggle("Hide received lines", () -> c().hideReceived, v -> c().hideReceived = v));
		list.add(toggle("Hide sent lines", () -> c().hideSent, v -> c().hideSent = v));
		return list;
	}

	private List<Cell> profit() {
		List<Cell> list = new ArrayList<>();
		list.add(toggle("Profit HUD", () -> c().hudVisible, v -> c().hudVisible = v));
		list.add(cycle("Period", () -> c().profitScope.label(), () -> {
			ZyroConfig.ProfitScope[] values = ZyroConfig.ProfitScope.values();
			c().profitScope = values[(c().profitScope.ordinal() + 1) % values.length];
		}));
		list.add(toggle("Border", () -> c().hudBorder, v -> c().hudBorder = v));
		list.add(toggle("Accent bar", () -> c().hudAccentBar, v -> c().hudAccentBar = v));
		list.add(stepper("Opacity", () -> c().hudOpacity, v -> c().hudOpacity = v, 0, 100, 5, v -> v + "%"));
		list.add(colorField("Title", () -> c().hudTitleColor, v -> c().hudTitleColor = v));
		list.add(colorField("Gain", () -> c().hudGainColor, v -> c().hudGainColor = v));
		list.add(colorField("Loss", () -> c().hudLossColor, v -> c().hudLossColor = v));
		list.add(colorField("Zero", () -> c().hudZeroColor, v -> c().hudZeroColor = v));
		list.add(colorField("Background", () -> c().hudBgColor, v -> c().hudBgColor = v));
		list.add(colorField("Accent", () -> c().hudAccentColor, v -> c().hudAccentColor = v));
		list.add(action("Move HUDs", () -> this.minecraft.gui.setScreen(new HudEditorScreen(this))));
		return list;
	}

	private List<Cell> streak() {
		List<Cell> list = new ArrayList<>();
		list.add(toggle("Win streak", () -> c().streakVisible, v -> c().streakVisible = v));
		list.add(toggle("Separate box", () -> c().streakSeparate, v -> c().streakSeparate = v));
		list.add(toggle("Border", () -> c().streakBorder, v -> c().streakBorder = v));
		list.add(toggle("Accent bar", () -> c().streakAccentBar, v -> c().streakAccentBar = v));
		list.add(stepper("Opacity", () -> c().streakOpacity, v -> c().streakOpacity = v, 0, 100, 5, v -> v + "%"));
		list.add(stepper("Streak", () -> c().winStreak, v -> c().winStreak = v, 0, 9999, 1, String::valueOf));
		list.add(colorField("Label", () -> c().streakLabelColor, v -> c().streakLabelColor = v));
		list.add(colorField("Number", () -> c().streakNumberColor, v -> c().streakNumberColor = v));
		list.add(colorField("Background", () -> c().streakBgColor, v -> c().streakBgColor = v));
		list.add(colorField("Accent", () -> c().streakAccentColor, v -> c().streakAccentColor = v));
		list.add(action("Reset streak", () -> {
			c().winStreak = 0;
			save();
		}));
		return list;
	}

	private List<Cell> popup() {
		List<Cell> list = new ArrayList<>();
		list.add(toggle("Popup alert", () -> c().popupsEnabled, v -> c().popupsEnabled = v));
		list.add(cycle("Minimum", () -> MoneyFormat.compact(c().popupMin), () -> c().popupMin = nextAmount(c().popupMin, false)));
		list.add(stepper("Duration", () -> (int) Math.round(c().popupDurationSec * 10), v -> c().popupDurationSec = v / 10.0,
				5, 150, 5, v -> (v / 10.0) + "s"));
		list.add(cycle("Animation", () -> c().popupAnimation.label(), () -> {
			ZyroConfig.PopupAnimation[] values = ZyroConfig.PopupAnimation.values();
			c().popupAnimation = values[(c().popupAnimation.ordinal() + 1) % values.length];
		}));
		list.add(stepper("Opacity", () -> c().popupOpacity, v -> c().popupOpacity = v, 20, 100, 5, v -> v + "%"));
		list.add(stepper("Border", () -> c().popupBorderWidth, v -> c().popupBorderWidth = v, 1, 4, 1, v -> v + "px"));
		list.add(toggle("Rainbow border", () -> c().popupRainbow, v -> c().popupRainbow = v));
		list.add(toggle("Glow", () -> c().popupGlow, v -> c().popupGlow = v));
		list.add(toggle("Sparkles", () -> c().popupSparkles, v -> c().popupSparkles = v));
		list.add(stepper("Sparkle count", () -> c().popupSparkleCount, v -> c().popupSparkleCount = v, 0, 24, 2, String::valueOf));
		list.add(toggle("Count up", () -> c().popupCountUp, v -> c().popupCountUp = v));
		list.add(toggle("Show + sign", () -> c().popupPlus, v -> c().popupPlus = v));
		list.add(toggle("Timer bar", () -> c().popupTimerBar, v -> c().popupTimerBar = v));
		list.add(colorField("Name", () -> c().popupNameColor, v -> c().popupNameColor = v));
		list.add(colorField("Amount", () -> c().popupAmountColor, v -> c().popupAmountColor = v));
		list.add(colorField("Border", () -> c().popupBorderColor, v -> c().popupBorderColor = v));
		list.add(colorField("Background", () -> c().popupBgColor, v -> c().popupBgColor = v));
		list.add(colorField("Timer bar", () -> c().popupTimerColor, v -> c().popupTimerColor = v));
		list.add(action("Test popup", PopupManager::test));
		list.add(action("Move popup", () -> this.minecraft.gui.setScreen(new HudEditorScreen(this))));
		return list;
	}

	private List<Cell> sounds() {
		List<Cell> list = new ArrayList<>();
		list.add(toggle("Sounds", () -> c().soundEnabled, v -> c().soundEnabled = v));
		list.add(cycle("Sound", () -> Platform.SOUND_NAMES[c().soundIndex % Platform.SOUND_NAMES.length],
				() -> c().soundIndex = (c().soundIndex + 1) % Platform.SOUND_NAMES.length));
		list.add(stepper("Volume", () -> c().soundVolume, v -> c().soundVolume = v, 0, 100, 10, v -> v + "%"));
		list.add(stepper("Pitch", () -> c().soundPitch, v -> c().soundPitch = v, 5, 20, 1, v -> String.valueOf(v / 10.0)));
		list.add(stepper("Repeat", () -> c().soundRepeat, v -> c().soundRepeat = v, 1, 5, 1, v -> v + "x"));
		list.add(action("Test sound", () -> {
			boolean was = c().soundEnabled;
			c().soundEnabled = true;
			PopupManager.playConfiguredSound();
			c().soundEnabled = was;
		}));
		return list;
	}

	private List<Cell> auction() {
		List<Cell> list = new ArrayList<>();
		list.add(toggle("Auction HUD", () -> c().auctionHudVisible, v -> c().auctionHudVisible = v));
		list.add(stepper("Rows", () -> c().auctionRows, v -> c().auctionRows = v, 1, 12, 1, String::valueOf));
		list.add(stepper("Opacity", () -> c().auctionHudOpacity, v -> c().auctionHudOpacity = v, 0, 100, 5, v -> v + "%"));
		list.add(stepper("Show result", () -> c().auctionResultSeconds, v -> c().auctionResultSeconds = v, 0, 600, 30, v -> v + "s"));
		list.add(toggle("Countdown warnings", () -> c().auctionWarnings, v -> c().auctionWarnings = v));
		list.add(stepper("Anti-snipe", () -> c().auctionAntiSnipeSeconds, v -> c().auctionAntiSnipeSeconds = v, 0, 60, 5,
				v -> v == 0 ? "off" : "+" + v + "s"));
		list.add(toggle("Auction sounds", () -> c().auctionSounds, v -> c().auctionSounds = v));
		list.add(toggle("Auto announce in chat", () -> c().auctionAutoAnnounce, v -> c().auctionAutoAnnounce = v));
		list.add(action("Open auction", () -> this.minecraft.gui.setScreen(new AuctionScreen(this))));
		list.add(action("Move HUDs", () -> this.minecraft.gui.setScreen(new HudEditorScreen(this))));
		return list;
	}

	// ---------------------------------------------------------------------------------------------
	// Drawing (everything here is drawn after the widgets, in areas that hold no widgets)
	// ---------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (this.tab == 3 || this.tab == 4 || this.tab == 7) {
			ZyroHud.previewUntil = System.currentTimeMillis() + 400;
		}

		ZyroConfig config = c();
		int top = Colors.parse(config.themeTop, 0xFF0F2A4A);
		int bottom = Colors.parse(config.themeBottom, 0xFF06121F);
		int accent = Colors.parse(config.accent, 0xFF00E5FF);

		Gfx.gradient(graphics, 0, 0, this.width, 26, top, bottom);
		graphics.fill(0, 26, this.width, 27, accent);
		drawTitle(graphics, config, accent);
		Ui.rightAligned(graphics, this.font, "v0.3.0", this.width - 8, 9, Ui.MUTED);

		for (Label label : this.labels) {
			String text = label.text.get();
			String clipped = Ui.clip(this.font, text, label.maxWidth);
			int x = label.centered ? label.x - this.font.width(clipped) / 2 : label.x;
			Ui.text(graphics, this.font, clipped, x, label.y, label.color);
			if (label.swatchHex != null) {
				int swatch = Colors.parse(label.swatchHex.get(), 0xFF000000);
				graphics.fill(label.x - 12, label.y - 1, label.x - 2, label.y + 9, 0xFFFFFFFF);
				graphics.fill(label.x - 11, label.y, label.x - 3, label.y + 8, swatch);
			}
		}

		Ui.centered(graphics, this.font, "Page " + (this.page + 1) + " / " + this.pages,
				this.width / 2, this.height - 36, Ui.MUTED);
	}

	private void drawTitle(GuiGraphicsExtractor graphics, ZyroConfig config, int accent) {
		String title = ZyroofficialClient.MOD_NAME;
		int x = this.left;
		if (!config.rainbowHeader) {
			Ui.shadowText(graphics, this.font, title, x, 9, accent);
			return;
		}
		double time = (System.currentTimeMillis() % 4000) / 4000.0;
		for (int i = 0; i < title.length(); i++) {
			String ch = String.valueOf(title.charAt(i));
			Ui.shadowText(graphics, this.font, ch, x, 9, Colors.hsv(time + i * 0.05, 0.6, 1.0));
			x += this.font.width(ch);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(null);
	}
}
