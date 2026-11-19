package com.zyroofficial.chat;

import com.zyroofficial.config.ZyroConfig;
import com.zyroofficial.data.TransactionSource;
import com.zyroofficial.data.TransactionType;
import com.zyroofficial.util.Colors;
import com.zyroofficial.util.MoneyFormat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Reads incoming system messages with the user's rules. Three jobs, one per hook:
 * <ul>
 *   <li>{@link #allow}: hide payments you filtered out</li>
 *   <li>{@link #modify}: recolor payment lines</li>
 *   <li>{@link #onGameMessage}: count the payment (runs for hidden lines too)</li>
 * </ul>
 * Only server system messages are read. Player chat is ignored, so nobody can fake a payment line.
 */
public final class ChatDetector {
	private static final int MAX_LINE_LENGTH = 400;
	private static final Map<String, Long> RECENT_LINES = new HashMap<>();

	private ChatDetector() {
	}

	/** A rule that matched a line, and what it found. */
	public static final class Hit {
		public final ChatRule rule;
		public final ChatRule.Match match;

		Hit(ChatRule rule, ChatRule.Match match) {
			this.rule = rule;
			this.match = match;
		}
	}

	/** Strips formatting codes and surrounding whitespace so rules see plain text. */
	public static String clean(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replaceAll("§.", "").strip();
	}

	/** First enabled rule that matches the line, or null. */
	public static Hit find(String text) {
		ChatDetectionConfig config = ChatDetectionConfig.get();
		if (!config.enabled || text == null || text.isEmpty() || text.length() > MAX_LINE_LENGTH) {
			return null;
		}
		for (ChatRule rule : config.rules) {
			if (!rule.enabled || !config.typeEnabled(rule.type)) {
				continue;
			}
			ChatRule.Match match = rule.match(text);
			if (match != null) {
				return new Hit(rule, match);
			}
		}
		return null;
	}

	// ---------------------------------------------------------------------------------------------
	// Hook 1: counting
	// ---------------------------------------------------------------------------------------------

	public static void onGameMessage(Component message, boolean overlay) {
		ChatDetectionConfig config = ChatDetectionConfig.get();
		if (overlay && config.ignoreOverlay) {
			return;
		}
		process(clean(message.getString()), config);
	}

	/** Separated from the hook so it can be tested with plain text. */
	static void process(String text, ChatDetectionConfig config) {
		if (text.isEmpty()) {
			return;
		}
		if (config.captureRecent) {
			RecentMessages.add(text);
		}
		if (!config.enabled || text.length() > MAX_LINE_LENGTH) {
			return;
		}

		Hit hit = find(text);
		if (hit == null) {
			if (text.indexOf('$') >= 0 && text.matches(".*[0-9].*") && config.captureRecent) {
				RecentMessages.addUnmatched(text);
			}
			return;
		}
		if (isDuplicate(text, config)) {
			return;
		}
		PaymentProcessor.process(hit.rule.type, hit.match.amount, hit.match.player,
				hit.rule.name, TransactionSource.CHAT);
	}

	// ---------------------------------------------------------------------------------------------
	// Hook 2: hiding
	// ---------------------------------------------------------------------------------------------

	/** Returns false to hide the message. */
	public static boolean allow(Component message, boolean overlay) {
		ZyroConfig zyro = ZyroConfig.get();
		if (overlay || !(zyro.hideSmall || zyro.hideReceived || zyro.hideSent)) {
			return true;
		}
		return allowText(clean(message.getString()), zyro);
	}

	static boolean allowText(String text, ZyroConfig zyro) {
		Hit hit = find(text);
		if (hit == null) {
			return true;
		}
		TransactionType type = hit.rule.type;
		if (type == TransactionType.RECEIVED && zyro.hideReceived) {
			return false;
		}
		if (type == TransactionType.SENT && zyro.hideSent) {
			return false;
		}
		boolean payment = type == TransactionType.RECEIVED || type == TransactionType.SENT;
		return !(payment && zyro.hideSmall && hit.match.amount < zyro.hideBelowAmount);
	}

	// ---------------------------------------------------------------------------------------------
	// Hook 3: recoloring
	// ---------------------------------------------------------------------------------------------

	public static Component modify(Component message, boolean overlay) {
		ZyroConfig zyro = ZyroConfig.get();
		if (overlay || !zyro.chatColors) {
			return message;
		}
		String text = clean(message.getString());
		Hit hit = find(text);
		if (hit == null) {
			return message;
		}
		TransactionType type = hit.rule.type;
		if (type != TransactionType.RECEIVED && type != TransactionType.SENT) {
			return message;
		}
		return recolor(text, hit, zyro);
	}

	static Component recolor(String text, Hit hit, ZyroConfig zyro) {
		ChatRule.Match match = hit.match;
		boolean income = hit.rule.type == TransactionType.RECEIVED;
		int nameColor = Colors.parse(zyro.chatNameColor, 0xFFFFA500) & 0xFFFFFF;
		int amountColor = Colors.parse(income ? zyro.chatReceivedColor : zyro.chatSentColor, 0xFFFFFFFF) & 0xFFFFFF;

		List<int[]> segments = new ArrayList<>();
		if (match.playerStart >= 0 && match.playerEnd > match.playerStart) {
			segments.add(new int[] {match.playerStart, match.playerEnd, 0});
		}
		if (match.amountStart >= 0 && match.amountEnd > match.amountStart) {
			segments.add(new int[] {match.amountStart, match.amountEnd, 1});
		}
		segments.sort(Comparator.comparingInt(s -> s[0]));

		MutableComponent out = Component.empty();
		int pos = 0;
		for (int[] segment : segments) {
			if (segment[0] < pos || segment[1] > text.length()) {
				continue;
			}
			if (segment[0] > pos) {
				out.append(Component.literal(text.substring(pos, segment[0])));
			}
			String piece = text.substring(segment[0], segment[1]);
			boolean isName = segment[2] == 0;
			Style style = Style.EMPTY
					.withColor(TextColor.fromRgb(isName ? nameColor : amountColor))
					.withBold(isName ? zyro.boldNames : zyro.boldAmounts);
			out.append(Component.literal(piece).withStyle(style));
			pos = segment[1];
		}
		if (pos < text.length()) {
			out.append(Component.literal(text.substring(pos)));
		}

		if (!income && zyro.payTaxPercent > 0 && zyro.showTaxInChat) {
			double tax = match.amount * zyro.payTaxPercent / 100.0;
			int taxColor = Colors.parse(zyro.chatTaxColor, 0xFFAAAAAA) & 0xFFFFFF;
			out.append(Component.literal(" (+" + MoneyFormat.compact(tax) + " tax)")
					.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(taxColor))));
		}
		return out;
	}

	// ---------------------------------------------------------------------------------------------

	private static synchronized boolean isDuplicate(String text, ChatDetectionConfig config) {
		if (!config.duplicateFilterEnabled || config.duplicateWindowMs <= 0) {
			return false;
		}
		long now = System.currentTimeMillis();

		Iterator<Map.Entry<String, Long>> iterator = RECENT_LINES.entrySet().iterator();
		while (iterator.hasNext()) {
			if (now - iterator.next().getValue() > config.duplicateWindowMs) {
				iterator.remove();
			}
		}

		if (RECENT_LINES.containsKey(text)) {
			return true;
		}
		RECENT_LINES.put(text, now);
		return false;
	}

	/** For tests. */
	static synchronized void resetDuplicates() {
		RECENT_LINES.clear();
	}
}
