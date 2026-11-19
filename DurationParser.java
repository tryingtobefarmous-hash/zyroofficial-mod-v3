package com.zyroofficial.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses and formats durations typed by the user.
 * Accepts: 30s, 5m, 1h, 2d, 1h30m, 1h 30m 15s, 90 (bare number = minutes), 1:30:00, 5:00 (m:s).
 */
public final class DurationParser {
	public static final long MIN_MS = 1000L;
	public static final long MAX_MS = 30L * 24 * 60 * 60 * 1000;

	private static final Pattern TOKEN = Pattern.compile("\\s*(\\d+(?:\\.\\d+)?)\\s*([dhms]?)\\s*");
	private static final Pattern CLOCK = Pattern.compile("(\\d{1,3}):(\\d{1,2})(?::(\\d{1,2}))?");

	private DurationParser() {
	}

	/** Returns the duration in milliseconds, or -1 if the text is invalid or out of range (1s to 30 days). */
	public static long parse(String text) {
		if (text == null) {
			return -1;
		}
		String s = text.trim().toLowerCase(Locale.ROOT);
		if (s.isEmpty()) {
			return -1;
		}

		Matcher clock = CLOCK.matcher(s);
		if (clock.matches()) {
			long a = Long.parseLong(clock.group(1));
			long b = Long.parseLong(clock.group(2));
			long seconds;
			if (clock.group(3) != null) {
				seconds = a * 3600 + b * 60 + Long.parseLong(clock.group(3));
			} else {
				seconds = a * 60 + b;
			}
			return inRange(seconds * 1000.0);
		}

		Matcher m = TOKEN.matcher(s);
		int pos = 0;
		double total = 0;
		while (pos < s.length()) {
			m.region(pos, s.length());
			if (!m.lookingAt()) {
				return -1;
			}
			double value;
			try {
				value = Double.parseDouble(m.group(1));
			} catch (NumberFormatException e) {
				return -1;
			}
			double multiplier;
			switch (m.group(2)) {
				case "d":
					multiplier = 86_400_000.0;
					break;
				case "h":
					multiplier = 3_600_000.0;
					break;
				case "s":
					multiplier = 1000.0;
					break;
				default:
					multiplier = 60_000.0;
					break;
			}
			total += value * multiplier;
			pos = m.end();
		}
		return inRange(total);
	}

	private static long inRange(double ms) {
		if (Double.isNaN(ms) || ms < MIN_MS || ms > MAX_MS) {
			return -1;
		}
		return Math.round(ms);
	}

	/** 1:05:03, 04:32, or 2d 03:00:00. Rounds up so the display never shows 0:00 while time remains. */
	public static String format(long ms) {
		if (ms <= 0) {
			return "0:00";
		}
		long total = (ms + 999) / 1000;
		long d = total / 86_400;
		long h = (total % 86_400) / 3600;
		long m = (total % 3600) / 60;
		long s = total % 60;
		if (d > 0) {
			return String.format(Locale.US, "%dd %02d:%02d:%02d", d, h, m, s);
		}
		if (h > 0) {
			return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
		}
		return String.format(Locale.US, "%d:%02d", m, s);
	}
}
