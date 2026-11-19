package com.zyroofficial.auction;

import com.zyroofficial.ZyroofficialClient;
import com.zyroofficial.config.ZyroConfig;
import com.zyroofficial.util.JsonFiles;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runs the auction: starts it, counts payments from players, ends it when the timer runs out and keeps
 * the history. Saved to config/zyroofficial/auctions.json so a running auction survives a restart.
 */
public final class AuctionManager {
	private static final AuctionManager INSTANCE = new AuctionManager();
	private static final long[] WARNING_SECONDS = {60, 30, 10};
	private static final int MAX_HISTORY = 100;

	/** Hooks so the UI can react (chat message, popup, sound) without this class touching Minecraft. */
	public interface Listener {
		default void started(Auction auction) {
		}

		default void bid(Auction auction, Auction.Bid bid, boolean newLeader) {
		}

		default void warning(Auction auction, long secondsLeft) {
		}

		default void ended(Auction auction) {
		}

		default void cancelled(Auction auction) {
		}
	}

	private static final class FileData {
		int version = 1;
		Auction current;
		List<Auction> history = new ArrayList<>();
	}

	private Auction current;
	private List<Auction> history = new ArrayList<>();
	private Listener listener = new Listener() {
	};
	private final Set<Long> warned = new HashSet<>();

	private AuctionManager() {
	}

	public static AuctionManager get() {
		return INSTANCE;
	}

	private static Path file() {
		return ZyroConfig.directory().resolve("auctions.json");
	}

	public synchronized void setListener(Listener listener) {
		this.listener = listener == null ? new Listener() {
		} : listener;
	}

	public synchronized void load() {
		current = null;
		history = new ArrayList<>();
		FileData data = JsonFiles.read(file(), FileData.class);
		if (data == null) {
			return;
		}
		if (data.history != null) {
			history = new ArrayList<>(data.history);
		}
		current = data.current;
		if (current != null) {
			if (current.bids == null) {
				current.bids = new ArrayList<>();
			}
			if (current.status == null) {
				current.status = Auction.Status.ENDED;
			}
			if (current.mode == null) {
				current.mode = Auction.ScoreMode.TOTAL;
			}
			if (current.status == Auction.Status.RUNNING && System.currentTimeMillis() >= current.endMs) {
				finish(current.endMs, false);
			} else if (current.status == Auction.Status.RUNNING) {
				warned.clear();
			}
		}
	}

	public synchronized void save() {
		FileData data = new FileData();
		data.current = current;
		data.history = new ArrayList<>(history);
		try {
			JsonFiles.write(file(), data);
		} catch (IOException e) {
			ZyroofficialClient.LOGGER.error("Could not save Zyroofficial auctions", e);
		}
	}

	public synchronized boolean isRunning() {
		return current != null && current.status == Auction.Status.RUNNING;
	}

	/** The running auction, or the one that just ended (until a new one starts or it is cleared). */
	public synchronized Auction current() {
		return current;
	}

	public synchronized List<Auction> history() {
		return new ArrayList<>(history);
	}

	/** What the HUD should show: the running auction, or an ended one for a limited time. */
	public synchronized Auction display(long now, int resultSeconds) {
		if (current == null) {
			return null;
		}
		if (current.status == Auction.Status.RUNNING) {
			return current;
		}
		if (current.status == Auction.Status.ENDED && now - current.endedMs < resultSeconds * 1000L) {
			return current;
		}
		return null;
	}

	/** Starts an auction. Returns null if one is already running. */
	public synchronized Auction start(String item, long durationMs, double minBid, Auction.ScoreMode mode) {
		return start(item, durationMs, minBid, mode, System.currentTimeMillis());
	}

	synchronized Auction start(String item, long durationMs, double minBid, Auction.ScoreMode mode, long now) {
		if (isRunning()) {
			return null;
		}
		Auction auction = new Auction();
		auction.item = item.trim();
		auction.startMs = now;
		auction.endMs = now + durationMs;
		auction.minBid = Math.max(0, minBid);
		auction.mode = mode == null ? Auction.ScoreMode.TOTAL : mode;
		auction.status = Auction.Status.RUNNING;
		current = auction;
		warned.clear();
		save();
		listener.started(auction);
		return auction;
	}

	/** Counts a payment toward the running auction. Returns false if there is no running auction. */
	public synchronized boolean onPayment(String player, double amount, boolean manual) {
		return onPayment(player, amount, manual, System.currentTimeMillis());
	}

	synchronized boolean onPayment(String player, double amount, boolean manual, long now) {
		if (!isRunning() || now >= current.endMs || player == null || player.isBlank()
				|| amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
			return false;
		}
		String before = leaderName(current);

		Auction.Bid bid = new Auction.Bid();
		bid.player = player.trim();
		bid.amount = amount;
		bid.time = now;
		bid.manual = manual;
		current.bids.add(bid);

		int snipe = ZyroConfig.get().auctionAntiSnipeSeconds;
		if (snipe > 0 && current.endMs - now < snipe * 1000L) {
			current.endMs = now + snipe * 1000L;
			warned.clear();
		}

		String after = leaderName(current);
		boolean newLeader = !after.isEmpty() && !after.equalsIgnoreCase(before);
		save();
		listener.bid(current, bid, newLeader);
		return true;
	}

	private static String leaderName(Auction auction) {
		Auction.Standing leader = auction.leader();
		return leader == null ? "" : leader.player;
	}

	/** Call every tick. Ends the auction when time is up and fires the countdown warnings. */
	public synchronized void tick(long now) {
		if (!isRunning()) {
			return;
		}
		long remaining = current.endMs - now;
		if (remaining <= 0) {
			finish(now, true);
			return;
		}
		long duration = current.endMs - current.startMs;
		for (int i = WARNING_SECONDS.length - 1; i >= 0; i--) {
			long seconds = WARNING_SECONDS[i];
			if (remaining <= seconds * 1000L) {
				if (!warned.contains(seconds)) {
					for (long t : WARNING_SECONDS) {
						if (t >= seconds) {
							warned.add(t);
						}
					}
					if (duration > seconds * 1000L) {
						listener.warning(current, seconds);
					}
				}
				break;
			}
		}
	}

	/** Ends the running auction right now. Returns it, or null if none is running. */
	public synchronized Auction end() {
		if (!isRunning()) {
			return null;
		}
		finish(System.currentTimeMillis(), true);
		return current;
	}

	private void finish(long now, boolean notify) {
		current.status = Auction.Status.ENDED;
		current.endedMs = now;
		Auction.Standing leader = current.leader();
		if (leader != null) {
			current.winner = leader.player;
			current.winningAmount = leader.score;
		} else {
			current.winner = "";
			current.winningAmount = 0;
		}
		history.add(0, current);
		while (history.size() > MAX_HISTORY) {
			history.remove(history.size() - 1);
		}
		save();
		if (notify) {
			listener.ended(current);
		}
	}

	/** Cancels the running auction without a winner. */
	public synchronized boolean cancel() {
		if (!isRunning()) {
			return false;
		}
		Auction cancelled = current;
		cancelled.status = Auction.Status.CANCELLED;
		current = null;
		save();
		listener.cancelled(cancelled);
		return true;
	}

	/** Hides the finished auction from the HUD. */
	public synchronized void clearFinished() {
		if (current != null && current.status != Auction.Status.RUNNING) {
			current = null;
			save();
		}
	}

	/** Text used by the "announce" buttons. */
	public synchronized String announcement(long now) {
		if (current == null) {
			return "";
		}
		if (current.status == Auction.Status.RUNNING) {
			return current.leaderLine(now);
		}
		return current.resultLine();
	}

	public static String normalize(String name) {
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}
}
