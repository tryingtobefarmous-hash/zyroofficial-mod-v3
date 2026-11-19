package com.zyroofficial.chat;

import com.zyroofficial.ZyroofficialClient;
import com.zyroofficial.config.ZyroConfig;
import com.zyroofficial.data.TransactionType;
import com.zyroofficial.util.JsonFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Settings for automatic chat detection, saved to config/zyroofficial/chat-detection.json.
 *
 * <p>Two starter rules are added the first time (and kept editable or deletable):
 * they are based on the two payment lines shown in your reference screenshot,
 * "(player) paid you $5B" and "You paid (player) $1M". Everything can be changed in the menu.
 */
public final class ChatDetectionConfig {
	public static final String DEFAULT_PLAYER_PATTERN = "\\.?[A-Za-z0-9_]{2,17}";
	public static final String DEFAULT_AMOUNT_PATTERN =
			"\\$?[0-9][0-9,]*(?:\\.[0-9]+)?(?:[KkMmBbTt](?![A-Za-z]))?";

	// Earlier versions used these; a file that still holds them is upgraded to the new ones.
	private static final String OLD_PLAYER_PATTERN = "[A-Za-z0-9_]{3,16}";
	private static final String OLD_AMOUNT_PATTERN = "\\$?\\s?[0-9][0-9,]*(?:\\.[0-9]+)?\\s?[KkMmBbTt]?";

	private static ChatDetectionConfig instance;

	/** Bumped when a new version needs to migrate the saved file. */
	public int version = 0;

	/** Master switch. */
	public boolean enabled = true;

	/** Per-type switches: a rule whose type is switched off is skipped. */
	public boolean detectReceived = true;
	public boolean detectSent = true;
	public boolean detectWon = true;
	public boolean detectLost = true;
	public boolean detectTax = true;

	/** Ignore identical messages that arrive again within the window. */
	public boolean duplicateFilterEnabled = true;
	public int duplicateWindowMs = 1500;

	/** Ignore action-bar (overlay) messages. */
	public boolean ignoreOverlay = true;

	/** Keep the last 100 system messages in memory (never saved) so rules can be built from real lines. */
	public boolean captureRecent = true;

	/** Regex substituted for {player} in rules. */
	public String playerPattern = DEFAULT_PLAYER_PATTERN;
	/** Regex substituted for {amount} in rules. */
	public String amountPattern = DEFAULT_AMOUNT_PATTERN;

	public List<ChatRule> rules = new ArrayList<>();

	public static ChatDetectionConfig get() {
		if (instance == null) {
			load();
		}
		return instance;
	}

	private static Path file() {
		return ZyroConfig.directory().resolve("chat-detection.json");
	}

	public static void load() {
		ChatDetectionConfig loaded = JsonFiles.read(file(), ChatDetectionConfig.class);
		instance = loaded != null ? loaded : new ChatDetectionConfig();
		boolean changed = instance.sanitize();
		if (changed) {
			save();
		}
	}

	public static void save() {
		try {
			JsonFiles.write(file(), get());
		} catch (IOException e) {
			ZyroofficialClient.LOGGER.error("Could not save Zyroofficial chat detection settings", e);
		}
	}

	public boolean typeEnabled(TransactionType type) {
		switch (type) {
			case RECEIVED:
				return detectReceived;
			case SENT:
				return detectSent;
			case WON:
				return detectWon;
			case LOST:
				return detectLost;
			case TAX:
				return detectTax;
			default:
				return false;
		}
	}

	public void setTypeEnabled(TransactionType type, boolean value) {
		switch (type) {
			case RECEIVED:
				detectReceived = value;
				break;
			case SENT:
				detectSent = value;
				break;
			case WON:
				detectWon = value;
				break;
			case LOST:
				detectLost = value;
				break;
			case TAX:
				detectTax = value;
				break;
			default:
				break;
		}
	}

	/** True if the text is a valid regular expression. */
	public static boolean isValidRegex(String regex) {
		if (regex == null || regex.isBlank() || regex.length() > 200) {
			return false;
		}
		try {
			Pattern.compile(regex);
			return true;
		} catch (PatternSyntaxException e) {
			return false;
		}
	}

	/** The two starter rules, taken from the formats in the reference screenshot. */
	public static List<ChatRule> starterRules() {
		List<ChatRule> list = new ArrayList<>();

		ChatRule received = new ChatRule();
		received.id = "starter-received";
		received.name = "Payment received";
		received.type = TransactionType.RECEIVED;
		received.pattern = "{player} paid you {amount}";
		list.add(received);

		ChatRule sent = new ChatRule();
		sent.id = "starter-sent";
		sent.name = "Payment sent";
		sent.type = TransactionType.SENT;
		sent.pattern = "You paid {player} {amount}";
		list.add(sent);

		return list;
	}

	/** Returns true if anything was changed and the file should be saved. */
	private boolean sanitize() {
		boolean changed = false;
		if (rules == null) {
			rules = new ArrayList<>();
			changed = true;
		}
		rules.removeIf(rule -> rule == null);
		for (ChatRule rule : rules) {
			if (rule.id == null) {
				rule.id = java.util.UUID.randomUUID().toString();
			}
			if (rule.name == null) {
				rule.name = "Rule";
			}
			if (rule.pattern == null) {
				rule.pattern = "";
			}
			if (rule.type == null) {
				rule.type = TransactionType.RECEIVED;
			}
		}
		if (!isValidRegex(playerPattern)) {
			playerPattern = DEFAULT_PLAYER_PATTERN;
			changed = true;
		}
		if (!isValidRegex(amountPattern)) {
			amountPattern = DEFAULT_AMOUNT_PATTERN;
			changed = true;
		}
		int window = Math.max(0, Math.min(60_000, duplicateWindowMs));
		if (window != duplicateWindowMs) {
			duplicateWindowMs = window;
			changed = true;
		}

		if (version < 1) {
			if (OLD_PLAYER_PATTERN.equals(playerPattern)) {
				playerPattern = DEFAULT_PLAYER_PATTERN;
			}
			if (OLD_AMOUNT_PATTERN.equals(amountPattern)) {
				amountPattern = DEFAULT_AMOUNT_PATTERN;
			}
			if (rules.isEmpty()) {
				rules.addAll(starterRules());
			}
			version = 1;
			changed = true;
		}
		return changed;
	}
}
