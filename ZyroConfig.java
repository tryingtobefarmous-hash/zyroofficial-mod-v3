package com.zyroofficial.config;

import com.zyroofficial.ZyroofficialClient;
import com.zyroofficial.auction.Auction;
import com.zyroofficial.util.JsonFiles;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;

/**
 * All user settings, saved to config/zyroofficial/config.json.
 * Missing fields in an older file fall back to the defaults written below.
 */
public final class ZyroConfig {
	public enum ProfitScope {
		TODAY("Today"),
		SESSION("This session"),
		ALL_TIME("All time");

		private final String label;

		ProfitScope(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	public enum PopupAnimation {
		POP("Pop"),
		SLIDE("Slide"),
		FADE("Fade");

		private final String label;

		PopupAnimation(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	public static final class Theme {
		public final String name;
		public final String top;
		public final String bottom;
		public final String accent;

		Theme(String name, String top, String bottom, String accent) {
			this.name = name;
			this.top = top;
			this.bottom = bottom;
			this.accent = accent;
		}
	}

	public static final Theme[] THEMES = {
			new Theme("Ocean", "#0F2A4A", "#06121F", "#00E5FF"),
			new Theme("Midnight", "#1A1033", "#0A0614", "#B388FF"),
			new Theme("Forest", "#12351F", "#07140C", "#4CFF8A"),
			new Theme("Crimson", "#3A1218", "#14060A", "#FF5577"),
			new Theme("Graphite", "#2A2D33", "#101114", "#E0E0E0"),
			new Theme("Sunset", "#3D1F0F", "#140A05", "#FFB347")
	};

	/** Amounts the filters and the popup minimum cycle through. */
	public static final double[] AMOUNT_STEPS = {
			1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, 500_000_000, 1_000_000_000, 10_000_000_000D
	};

	private static ZyroConfig instance;

	public int configVersion = 3;

	// ---- General / theme ----
	public String themeName = "Ocean";
	public String themeTop = "#0F2A4A";
	public String themeBottom = "#06121F";
	public String accent = "#00E5FF";
	public int panelOpacity = 95;
	public int dimGame = 55;
	public boolean rainbowHeader = true;

	/** Master switch for the HUD boxes (profit, streak, auction board). The "show or hide HUD" key flips it. */
	public boolean hudMaster = true;

	// ---- Money ----
	/** Transactions below this amount are ignored. */
	public double minTransactionAmount = 0.0;
	public double payTaxPercent = 0.0;
	public boolean countTax = true;
	public boolean showTaxInChat = true;

	// ---- Chat colors ----
	public boolean chatColors = true;
	public boolean boldNames = true;
	public boolean boldAmounts = true;
	public String chatNameColor = "#FFA500";
	public String chatReceivedColor = "#7CFF3A";
	public String chatSentColor = "#FF4040";
	public String chatTaxColor = "#AAAAAA";

	// ---- Chat filter ----
	public boolean hideSmall = false;
	public double hideBelowAmount = 1_000;
	public boolean hideReceived = false;
	public boolean hideSent = false;

	// ---- Profit HUD ----
	public boolean hudVisible = true;
	public ProfitScope profitScope = ProfitScope.TODAY;
	public boolean hudBorder = true;
	public boolean hudAccentBar = true;
	public int hudOpacity = 60;
	public String hudTitleColor = "#FFC400";
	public String hudGainColor = "#7CFF3A";
	public String hudLossColor = "#FF4040";
	public String hudZeroColor = "#FFFFFF";
	public String hudBgColor = "#0B0B14";
	public String hudAccentColor = "#FFAA00";
	public int hudX = 6;
	public int hudY = 6;

	// ---- Win streak ----
	public boolean streakVisible = true;
	public boolean streakSeparate = false;
	public boolean streakBorder = true;
	public boolean streakAccentBar = true;
	public int streakOpacity = 40;
	public String streakLabelColor = "#AAAAAA";
	public String streakNumberColor = "#FFD700";
	public String streakBgColor = "#0B0B14";
	public String streakAccentColor = "#FF8A00";
	public int streakX = 6;
	public int streakY = 60;
	public int winStreak = 0;

	// ---- Popup ----
	public boolean popupsEnabled = true;
	public double popupMin = 10_000;
	public double popupDurationSec = 2.0;
	public PopupAnimation popupAnimation = PopupAnimation.POP;
	public int popupOpacity = 95;
	public int popupBorderWidth = 1;
	public boolean popupRainbow = true;
	public boolean popupGlow = true;
	public boolean popupSparkles = true;
	public int popupSparkleCount = 8;
	public boolean popupCountUp = true;
	public boolean popupPlus = true;
	public boolean popupTimerBar = true;
	public String popupNameColor = "#FFA500";
	public String popupAmountColor = "#7CFF3A";
	public String popupBorderColor = "#FFD700";
	public String popupBgColor = "#140C28";
	public String popupTimerColor = "#FFD700";
	/** Offset from the horizontal center of the screen, and distance from the top. */
	public int popupX = 0;
	public int popupY = 36;

	// ---- Sounds ----
	public boolean soundEnabled = true;
	public int soundIndex = 0;
	public int soundVolume = 100;
	/** Pitch in tenths: 10 = 1.0 */
	public int soundPitch = 10;
	public int soundRepeat = 1;

	// ---- Auction ----
	public boolean auctionHudVisible = true;
	public int auctionX = 6;
	public int auctionY = 100;
	public int auctionHudOpacity = 70;
	public int auctionRows = 5;
	public int auctionResultSeconds = 120;
	public boolean auctionWarnings = true;
	public int auctionAntiSnipeSeconds = 0;
	public boolean auctionSounds = true;
	public boolean auctionAutoAnnounce = false;
	public Auction.ScoreMode auctionMode = Auction.ScoreMode.TOTAL;
	public String auctionDurationText = "5m";
	public String auctionMinText = "0";

	public static Path directory() {
		return FabricLoader.getInstance().getConfigDir().resolve("zyroofficial");
	}

	private static Path file() {
		return directory().resolve("config.json");
	}

	public static ZyroConfig get() {
		if (instance == null) {
			load();
		}
		return instance;
	}

	public static void load() {
		ZyroConfig loaded = JsonFiles.read(file(), ZyroConfig.class);
		instance = loaded != null ? loaded : new ZyroConfig();
		instance.sanitize();
	}

	public static void save() {
		try {
			JsonFiles.write(file(), get());
		} catch (IOException e) {
			ZyroofficialClient.LOGGER.error("Could not save Zyroofficial config", e);
		}
	}

	/** Puts every setting back to its default. Money history and auctions are not touched. */
	public static void reset() {
		instance = new ZyroConfig();
		save();
	}

	private void sanitize() {
		if (minTransactionAmount < 0 || Double.isNaN(minTransactionAmount)) {
			minTransactionAmount = 0.0;
		}
		if (payTaxPercent < 0 || payTaxPercent > 100 || Double.isNaN(payTaxPercent)) {
			payTaxPercent = 0.0;
		}
		hudX = Math.max(0, hudX);
		hudY = Math.max(0, hudY);
		streakX = Math.max(0, streakX);
		streakY = Math.max(0, streakY);
		auctionX = Math.max(0, auctionX);
		auctionY = Math.max(0, auctionY);
		popupY = Math.max(0, popupY);
		panelOpacity = clamp(panelOpacity, 30, 100);
		dimGame = clamp(dimGame, 0, 90);
		popupOpacity = clamp(popupOpacity, 20, 100);
		hudOpacity = clamp(hudOpacity, 0, 100);
		streakOpacity = clamp(streakOpacity, 0, 100);
		auctionHudOpacity = clamp(auctionHudOpacity, 0, 100);
		auctionRows = clamp(auctionRows, 1, 12);
		popupSparkleCount = clamp(popupSparkleCount, 0, 24);
		popupBorderWidth = clamp(popupBorderWidth, 1, 4);
		soundVolume = clamp(soundVolume, 0, 100);
		soundPitch = clamp(soundPitch, 5, 20);
		soundRepeat = clamp(soundRepeat, 1, 5);
		soundIndex = Math.max(0, soundIndex);
		if (popupDurationSec < 0.5 || popupDurationSec > 15 || Double.isNaN(popupDurationSec)) {
			popupDurationSec = 2.0;
		}
		if (profitScope == null) {
			profitScope = ProfitScope.TODAY;
		}
		if (popupAnimation == null) {
			popupAnimation = PopupAnimation.POP;
		}
		if (auctionMode == null) {
			auctionMode = Auction.ScoreMode.TOTAL;
		}
		if (auctionDurationText == null) {
			auctionDurationText = "5m";
		}
		if (auctionMinText == null) {
			auctionMinText = "0";
		}
	}

	public static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
