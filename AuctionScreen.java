package com.zyroofficial.screen;

import com.zyroofficial.auction.Auction;
import com.zyroofficial.auction.AuctionManager;
import com.zyroofficial.config.ZyroConfig;
import com.zyroofficial.util.AmountParser;
import com.zyroofficial.util.DurationParser;
import com.zyroofficial.util.MoneyFormat;
import com.zyroofficial.util.Platform;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Start an auction with any timer, watch who is winning (who paid the most) live, and see the winner
 * and the amount when the timer ends. Payments are counted automatically from chat; you can also add one by hand.
 */
public class AuctionScreen extends Screen {
	private static String lastItem = "";

	private final Screen parent;

	private EditBox itemBox;
	private EditBox durationBox;
	private EditBox minBox;
	private EditBox playerBox;
	private EditBox amountBox;
	private Button modeButton;

	private String message = "";
	private int messageColor = Ui.MUTED;

	private int left;
	private int colW;
	private int rightX;
	private int top;

	public AuctionScreen(Screen parent) {
		super(Component.literal("Auction"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int total = Math.min(this.width - 20, 420);
		this.colW = (total - 10) / 2;
		this.left = (this.width - total) / 2;
		this.rightX = this.left + this.colW + 10;
		this.top = 30;
		ZyroConfig config = ZyroConfig.get();
		int half = (this.colW - 4) / 2;

		this.itemBox = new EditBox(this.font, this.left, this.top + 10, this.colW, 20, Component.literal("Item"));
		this.itemBox.setMaxLength(40);
		this.itemBox.setHint(Component.literal("What are you auctioning?"));
		this.itemBox.setValue(lastItem);
		this.addRenderableWidget(this.itemBox);

		this.durationBox = new EditBox(this.font, this.left, this.top + 42, half, 20, Component.literal("Timer"));
		this.durationBox.setMaxLength(16);
		this.durationBox.setHint(Component.literal("5m, 1h30m, 2d"));
		this.durationBox.setValue(config.auctionDurationText);
		this.addRenderableWidget(this.durationBox);

		this.minBox = new EditBox(this.font, this.left + half + 4, this.top + 42, half, 20, Component.literal("Min bid"));
		this.minBox.setMaxLength(16);
		this.minBox.setHint(Component.literal("0 = none"));
		this.minBox.setValue(config.auctionMinText);
		this.addRenderableWidget(this.minBox);

		this.modeButton = this.addRenderableWidget(Button.builder(modeLabel(), button -> {
					ZyroConfig c = ZyroConfig.get();
					c.auctionMode = c.auctionMode == Auction.ScoreMode.TOTAL
							? Auction.ScoreMode.HIGHEST : Auction.ScoreMode.TOTAL;
					ZyroConfig.save();
					button.setMessage(modeLabel());
				})
				.bounds(this.left, this.top + 66, this.colW, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Start"), button -> start())
				.bounds(this.left, this.top + 90, half, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("End now"), button -> {
					Auction ended = AuctionManager.get().end();
					setMessage(ended == null ? "No auction is running." : "Ended.", ended == null ? Ui.RED : Ui.GREEN);
				})
				.bounds(this.left + half + 4, this.top + 90, half, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> {
					boolean cancelled = AuctionManager.get().cancel();
					setMessage(cancelled ? "Auction cancelled." : "No auction is running.", cancelled ? Ui.GREEN : Ui.RED);
				})
				.bounds(this.left, this.top + 114, half, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Announce"), button -> announce())
				.bounds(this.left + half + 4, this.top + 114, half, 20).build());

		this.playerBox = new EditBox(this.font, this.left, this.top + 150, half, 20, Component.literal("Player"));
		this.playerBox.setMaxLength(24);
		this.playerBox.setHint(Component.literal("Player"));
		this.addRenderableWidget(this.playerBox);

		this.amountBox = new EditBox(this.font, this.left + half + 4, this.top + 150, half, 20, Component.literal("Amount"));
		this.amountBox.setMaxLength(16);
		this.amountBox.setHint(Component.literal("Amount"));
		this.addRenderableWidget(this.amountBox);

		this.addRenderableWidget(Button.builder(Component.literal("Add payment"), button -> addPayment())
				.bounds(this.left, this.top + 174, this.colW, 20).build());

		this.addRenderableWidget(Button.builder(Component.literal("Auction settings"),
						button -> this.minecraft.gui.setScreen(new ZyroScreen(7, 0)))
				.bounds(this.rightX, this.height - 50, this.colW, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Back"), button -> this.onClose())
				.bounds(this.rightX, this.height - 26, this.colW, 20).build());
	}

	private static Component modeLabel() {
		return Component.literal("Winner: " + ZyroConfig.get().auctionMode.label());
	}

	private void start() {
		String item = this.itemBox.getValue().trim();
		if (item.isEmpty()) {
			setMessage("Type what you are auctioning first.", Ui.RED);
			return;
		}
		long duration = DurationParser.parse(this.durationBox.getValue());
		if (duration < 0) {
			setMessage("Timer not understood. Try 30s, 5m, 1h30m or 2d.", Ui.RED);
			return;
		}
		String minText = this.minBox.getValue().trim();
		double minBid = 0;
		if (!minText.isEmpty()) {
			minBid = AmountParser.parseAllowZero(minText);
			if (Double.isNaN(minBid)) {
				setMessage("Minimum bid not understood. Try 0, 500K or 2M.", Ui.RED);
				return;
			}
		}
		ZyroConfig config = ZyroConfig.get();
		Auction auction = AuctionManager.get().start(item, duration, minBid, config.auctionMode);
		if (auction == null) {
			setMessage("An auction is already running. End or cancel it first.", Ui.RED);
			return;
		}
		lastItem = item;
		config.auctionDurationText = this.durationBox.getValue().trim();
		config.auctionMinText = minText.isEmpty() ? "0" : minText;
		ZyroConfig.save();
		setMessage("Started: " + item + " for " + DurationParser.format(duration) + ".", Ui.GREEN);
	}

	private void announce() {
		String text = AuctionManager.get().announcement(System.currentTimeMillis());
		if (text.isEmpty()) {
			setMessage("Nothing to announce yet.", Ui.RED);
			return;
		}
		if (Platform.sendChat(text)) {
			setMessage("Sent to chat.", Ui.GREEN);
		} else {
			Platform.local(text);
			setMessage("Could not send to chat - shown to you only.", Ui.GOLD);
		}
	}

	private void addPayment() {
		String player = this.playerBox.getValue().trim();
		double amount = AmountParser.parse(this.amountBox.getValue());
		if (player.isEmpty() || Double.isNaN(amount)) {
			setMessage("Enter a player and an amount like 2.5M.", Ui.RED);
			return;
		}
		if (AuctionManager.get().onPayment(player, amount, true)) {
			this.amountBox.setValue("");
			setMessage("Added " + MoneyFormat.compact(amount) + " from " + player + ".", Ui.GREEN);
		} else {
			setMessage("No running auction to add to.", Ui.RED);
		}
	}

	private void setMessage(String text, int color) {
		this.message = text;
		this.messageColor = color;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		Ui.shadowText(graphics, this.font, "Auction", this.left, 12, Ui.ACCENT);
		Ui.text(graphics, this.font, "Item", this.left, this.top, Ui.MUTED);
		Ui.text(graphics, this.font, "Timer", this.left, this.top + 32, Ui.MUTED);
		int half = (this.colW - 4) / 2;
		Ui.text(graphics, this.font, "Min bid", this.left + half + 4, this.top + 32, Ui.MUTED);
		Ui.text(graphics, this.font, "Add a payment by hand", this.left, this.top + 140, Ui.MUTED);
		if (!this.message.isEmpty()) {
			Ui.text(graphics, this.font, Ui.clip(this.font, this.message, this.colW), this.left, this.top + 200, this.messageColor);
		}

		drawBoard(graphics);
	}

	private void drawBoard(GuiGraphicsExtractor graphics) {
		int boardTop = this.top;
		int boardH = this.height - 60 - boardTop;
		Ui.card(graphics, this.rightX, boardTop, this.colW, boardH);

		int x = this.rightX + 8;
		int y = boardTop + 6;
		int maxW = this.colW - 14;
		AuctionManager manager = AuctionManager.get();
		Auction auction = manager.current();
		long now = System.currentTimeMillis();

		if (auction == null) {
			Ui.text(graphics, this.font, "No auction running.", x, y, Ui.MUTED);
			Ui.text(graphics, this.font, "Fill in the form and press Start.", x, y + 11, Ui.MUTED);
			Ui.text(graphics, this.font, "Players pay you during the timer.", x, y + 22, Ui.MUTED);
			drawHistory(graphics, manager, x, y + 44, maxW, boardTop + boardH);
			return;
		}

		Ui.shadowText(graphics, this.font, Ui.clip(this.font, auction.item, maxW), x, y, Ui.GOLD);
		y += 12;
		if (auction.status == Auction.Status.RUNNING) {
			long left = Math.max(0, auction.endMs - now);
			Ui.text(graphics, this.font, "Time left: " + DurationParser.format(left), x, y, left < 10_000 ? Ui.RED : Ui.TEXT);
		} else {
			String line = auction.winner == null || auction.winner.isEmpty()
					? "Ended - no winner"
					: "WINNER: " + auction.winner + " - " + MoneyFormat.compact(auction.winningAmount);
			Ui.shadowText(graphics, this.font, Ui.clip(this.font, line, maxW), x, y, Ui.GREEN);
		}
		y += 12;
		if (auction.minBid > 0) {
			Ui.text(graphics, this.font, "Minimum: " + MoneyFormat.compact(auction.minBid), x, y, Ui.MUTED);
			y += 11;
		}
		y += 2;

		List<Auction.Standing> list = auction.standings();
		Auction.Standing leader = auction.leader();
		if (list.isEmpty()) {
			Ui.text(graphics, this.font, "No payments yet.", x, y, Ui.MUTED);
			y += 12;
		}
		int limit = boardTop + boardH - 4;
		for (int i = 0; i < list.size() && y + 10 <= limit - 30; i++) {
			Auction.Standing standing = list.get(i);
			int color = standing == leader ? Ui.GOLD : Ui.TEXT;
			String right = MoneyFormat.compact(standing.score);
			Ui.text(graphics, this.font, Ui.clip(this.font, (i + 1) + ". " + standing.player, maxW - this.font.width(right) - 6),
					x, y, color);
			Ui.rightAligned(graphics, this.font, right, this.rightX + this.colW - 6, y, color);
			y += 10;
		}
		drawHistory(graphics, manager, x, y + 4, maxW, limit);
	}

	private void drawHistory(GuiGraphicsExtractor graphics, AuctionManager manager, int x, int y, int maxW, int limit) {
		List<Auction> history = manager.history();
		if (history.isEmpty() || y + 10 > limit) {
			return;
		}
		Ui.text(graphics, this.font, "Past auctions", x, y, Ui.ACCENT);
		y += 11;
		for (Auction past : history) {
			if (y + 10 > limit) {
				break;
			}
			String line = past.item + " - " + (past.winner == null || past.winner.isEmpty()
					? "no winner" : past.winner + " " + MoneyFormat.compact(past.winningAmount));
			Ui.text(graphics, this.font, Ui.clip(this.font, line, maxW), x, y, Ui.MUTED);
			y += 10;
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
