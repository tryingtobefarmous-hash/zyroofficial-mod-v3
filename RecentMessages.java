package com.zyroofficial.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory buffers of the most recent system messages, so you can see the real lines your server sends
 * and turn them into rules. Never written to disk.
 *
 * <p>The "unmatched money" buffer holds lines that contain a $ amount but matched none of your rules.
 * If your payments are not being counted, they show up there.
 */
public final class RecentMessages {
	private static final int MAX = 100;
	private static final int MAX_UNMATCHED = 40;
	private static final Deque<String> BUFFER = new ArrayDeque<>();
	private static final Deque<String> UNMATCHED = new ArrayDeque<>();

	private static int seen;
	private static int unmatchedCount;

	private RecentMessages() {
	}

	public static synchronized void add(String line) {
		seen++;
		BUFFER.addFirst(line);
		while (BUFFER.size() > MAX) {
			BUFFER.removeLast();
		}
	}

	public static synchronized void addUnmatched(String line) {
		unmatchedCount++;
		UNMATCHED.addFirst(line);
		while (UNMATCHED.size() > MAX_UNMATCHED) {
			UNMATCHED.removeLast();
		}
	}

	/** Newest message first. */
	public static synchronized List<String> snapshot() {
		return new ArrayList<>(BUFFER);
	}

	/** Newest first: lines with a $ amount that matched no rule. */
	public static synchronized List<String> snapshotUnmatched() {
		return new ArrayList<>(UNMATCHED);
	}

	public static synchronized int seenCount() {
		return seen;
	}

	public static synchronized int unmatchedCount() {
		return unmatchedCount;
	}

	public static synchronized void clear() {
		BUFFER.clear();
		UNMATCHED.clear();
		seen = 0;
		unmatchedCount = 0;
	}
}
