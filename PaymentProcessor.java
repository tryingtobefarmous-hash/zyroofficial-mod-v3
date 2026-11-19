package com.zyroofficial.chat;

import com.zyroofficial.auction.AuctionManager;
import com.zyroofficial.config.ZyroConfig;
import com.zyroofficial.data.Transaction;
import com.zyroofficial.data.TransactionSource;
import com.zyroofficial.data.TransactionStore;
import com.zyroofficial.data.TransactionType;
import com.zyroofficial.hud.PopupManager;

/** Everything that happens when a payment is detected: record it, add tax, feed the auction, show the popup. */
public final class PaymentProcessor {
	private PaymentProcessor() {
	}

	public static void process(TransactionType type, double amount, String player, String note, TransactionSource source) {
		ZyroConfig config = ZyroConfig.get();
		TransactionStore store = TransactionStore.get();
		String name = player == null ? "" : player;

		// The auction has its own minimum bid, so it hears about every payment from a named player.
		if (type == TransactionType.RECEIVED && !name.isBlank()) {
			AuctionManager.get().onPayment(name, amount, false);
		}

		boolean added = store.add(new Transaction(type, source, amount, name, note));
		if (!added) {
			return;
		}

		if (type == TransactionType.SENT && config.payTaxPercent > 0 && config.countTax) {
			double tax = amount * config.payTaxPercent / 100.0;
			if (tax > 0) {
				store.add(new Transaction(TransactionType.TAX, source, tax, name, "Tax on payment"));
			}
		}

		if (type == TransactionType.RECEIVED && config.popupsEnabled && amount >= config.popupMin) {
			PopupManager.showPayment(name, amount);
		}
	}
}
