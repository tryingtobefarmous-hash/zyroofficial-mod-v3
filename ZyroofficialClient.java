package com.zyroofficial;

import com.zyroofficial.auction.Auction;
import com.zyroofficial.auction.AuctionManager;
import com.zyroofficial.chat.ChatDetectionConfig;
import com.zyroofficial.chat.ChatDetector;
import com.zyroofficial.config.ZyroConfig;
import com.zyroofficial.data.TransactionStore;
import com.zyroofficial.hud.PopupManager;
import com.zyroofficial.hud.ZyroHud;
import com.zyroofficial.input.ZyroKeybinds;
import com.zyroofficial.screen.AuctionScreen;
import com.zyroofficial.screen.HudEditorScreen;
import com.zyroofficial.screen.MoneyStatsScreen;
import com.zyroofficial.screen.ZyroScreen;
import com.zyroofficial.util.MoneyFormat;
import com.zyroofficial.util.Platform;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Client entrypoint for Zyroofficial Mod. */
public class ZyroofficialClient implements ClientModInitializer {
	public static final String MOD_ID = "zyroofficial";
	public static final String MOD_NAME = "Zyroofficial Mod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

	@Override
	public void onInitializeClient() {
		ZyroConfig.load();
		ChatDetectionConfig.load();
		TransactionStore.get().load();
		AuctionManager.get().load();
		AuctionManager.get().setListener(new AuctionHooks());

		ZyroKeybinds.init();
		ZyroHud.register();
		ClientTickEvents.END_CLIENT_TICK.register(ZyroofficialClient::onClientTick);

		// System messages only: player chat is never read, so nobody can fake a payment line.
		ClientReceiveMessageEvents.ALLOW_GAME.register(ChatDetector::allow);
		ClientReceiveMessageEvents.MODIFY_GAME.register(ChatDetector::modify);
		ClientReceiveMessageEvents.GAME.register(ChatDetector::onGameMessage);
		// Lines you chose to hide are still counted.
		ClientReceiveMessageEvents.GAME_CANCELED.register(ChatDetector::onGameMessage);

		LOGGER.info("{} loaded ({} saved transactions, {} chat rules)", MOD_NAME,
				TransactionStore.get().size(), ChatDetectionConfig.get().rules.size());
	}

	/** Chat message, popup and sound for auction events. */
	private static final class AuctionHooks implements AuctionManager.Listener {
		@Override
		public void started(Auction auction) {
			Platform.local("Auction started: " + auction.item + " - "
					+ com.zyroofficial.util.DurationParser.format(auction.endMs - auction.startMs));
			if (ZyroConfig.get().auctionAutoAnnounce) {
				Platform.sendChat("AUCTION: " + auction.item + " - pay me to bid! Ends in "
						+ com.zyroofficial.util.DurationParser.format(auction.endMs - auction.startMs));
			}
		}

		@Override
		public void bid(Auction auction, Auction.Bid bid, boolean newLeader) {
			if (!newLeader) {
				return;
			}
			Auction.Standing leader = auction.leader();
			if (leader == null) {
				return;
			}
			Platform.local("New leader: " + leader.player + " with " + MoneyFormat.compact(leader.score));
			ZyroConfig config = ZyroConfig.get();
			PopupManager.showText(leader.player + " is winning", MoneyFormat.compact(leader.score),
					0xFFFFD700, 2500, config.auctionSounds);
			if (config.auctionAutoAnnounce) {
				Platform.sendChat("AUCTION " + auction.item + ": " + leader.player + " leads with "
						+ MoneyFormat.compact(leader.score));
			}
		}

		@Override
		public void warning(Auction auction, long secondsLeft) {
			if (!ZyroConfig.get().auctionWarnings) {
				return;
			}
			Platform.local(auction.leaderLine(System.currentTimeMillis()));
			if (ZyroConfig.get().auctionAutoAnnounce) {
				Platform.sendChat(auction.leaderLine(System.currentTimeMillis()));
			}
		}

		@Override
		public void ended(Auction auction) {
			ZyroConfig config = ZyroConfig.get();
			Platform.local(auction.resultLine());
			boolean hasWinner = auction.winner != null && !auction.winner.isEmpty();
			PopupManager.showText(hasWinner ? "Auction won by " + auction.winner : "Auction ended - no winner",
					hasWinner ? MoneyFormat.compact(auction.winningAmount) + " for " + auction.item : auction.item,
					0xFF7CFF3A, 7000, config.auctionSounds);
			if (config.auctionAutoAnnounce) {
				Platform.sendChat(auction.resultLine());
			}
		}

		@Override
		public void cancelled(Auction auction) {
			Platform.local("Auction cancelled: " + auction.item);
		}
	}

	private static void onClientTick(Minecraft client) {
		AuctionManager.get().tick(System.currentTimeMillis());

		while (ZyroKeybinds.OPEN_MENU.consumeClick()) {
			open(client, new ZyroScreen());
		}
		while (ZyroKeybinds.OPEN_MONEY.consumeClick()) {
			open(client, new MoneyStatsScreen(null));
		}
		while (ZyroKeybinds.OPEN_AUCTION.consumeClick()) {
			open(client, new AuctionScreen(null));
		}
		while (ZyroKeybinds.MOVE_HUD.consumeClick()) {
			open(client, new HudEditorScreen(null));
		}
		while (ZyroKeybinds.TOGGLE_HUD.consumeClick()) {
			ZyroConfig config = ZyroConfig.get();
			config.hudMaster = !config.hudMaster;
			ZyroConfig.save();
			Platform.local(MOD_NAME + ": HUD " + (config.hudMaster ? "shown" : "hidden"));
		}
		while (ZyroKeybinds.WIN_PLUS.consumeClick()) {
			ZyroConfig config = ZyroConfig.get();
			config.winStreak++;
			ZyroConfig.save();
		}
		while (ZyroKeybinds.STREAK_RESET.consumeClick()) {
			ZyroConfig config = ZyroConfig.get();
			config.winStreak = 0;
			ZyroConfig.save();
		}
	}

	/** Opens a screen, but only when no other screen (chat, inventory, menus) is already open. */
	private static void open(Minecraft client, Screen screen) {
		if (client.player == null || client.gui.screen() != null) {
			return;
		}
		client.gui.setScreen(screen);
	}
}
