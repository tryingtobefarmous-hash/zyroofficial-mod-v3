package com.zyroofficial.auction;

import com.zyroofficial.util.DurationParser;
import com.zyroofficial.util.MoneyFormat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** One timed auction: players pay you during the timer, the biggest payer wins. */
public final class Auction {
	public enum Status {
		RUNNING,
		ENDED,
		CANCELLED
	}

	/** How a player's score is worked out from their payments. */
	public enum ScoreMode {
		TOTAL("Total paid"),
		HIGHEST("Highest single payment");

		private final String label;

		ScoreMode(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	public static final class Bid {
		public String player = "";
		public double amount;
		public long time;
		public boolean manual;
	}

	public static final class Standing {
		public String player = "";
		public double score;
		public double total;
		public double highest;
		public int payments;
		public long reachedAt;
		long highestAt;
		long lastAt;
	}

	public String id = UUID.randomUUID().toString();
	public String item = "";
	public long startMs;
	public long endMs;
	public double minBid;
	public ScoreMode mode = ScoreMode.TOTAL;
	public Status status = Status.RUNNING;
	public List<Bid> bids = new ArrayList<>();
	public String winner = "";
	public double winningAmount;
	public long endedMs;

	/** Everyone who has paid, best first. Ties go to whoever reached the score first. */
	public List<Standing> standings() {
		Map<String, Standing> map = new LinkedHashMap<>();
		for (Bid bid : bids) {
			String key = bid.player.toLowerCase(Locale.ROOT);
			Standing standing = map.get(key);
			if (standing == null) {
				standing = new Standing();
				map.put(key, standing);
			}
			standing.player = bid.player;
			standing.total += bid.amount;
			standing.payments++;
			if (bid.amount > standing.highest) {
				standing.highest = bid.amount;
				standing.highestAt = bid.time;
			}
			standing.lastAt = bid.time;
		}
		List<Standing> list = new ArrayList<>(map.values());
		for (Standing standing : list) {
			if (mode == ScoreMode.HIGHEST) {
				standing.score = standing.highest;
				standing.reachedAt = standing.highestAt;
			} else {
				standing.score = standing.total;
				standing.reachedAt = standing.lastAt;
			}
		}
		list.sort(Comparator.comparingDouble((Standing s) -> s.score).reversed()
				.thenComparingLong(s -> s.reachedAt));
		return list;
	}

	/** The best standing that meets the minimum bid, or null if nobody does. */
	public Standing leader() {
		for (Standing standing : standings()) {
			if (standing.score >= minBid && standing.score > 0) {
				return standing;
			}
		}
		return null;
	}

	public String leaderLine(long now) {
		Standing leader = leader();
		String left = DurationParser.format(endMs - now);
		if (leader == null) {
			return "AUCTION: " + item + " - no bids yet - " + left + " left";
		}
		return "AUCTION: " + item + " - leader " + leader.player + " with "
				+ MoneyFormat.compact(leader.score) + " - " + left + " left";
	}

	public String resultLine() {
		if (winner == null || winner.isEmpty()) {
			return "AUCTION ENDED: " + item + " - no winner";
		}
		return "AUCTION ENDED: " + item + " - winner " + winner + " with " + MoneyFormat.compact(winningAmount);
	}
}
