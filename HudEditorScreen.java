package com.zyroofficial.screen;

import com.zyroofficial.config.ZyroConfig;
import com.zyroofficial.hud.Gfx;
import com.zyroofficial.hud.PopupManager;
import com.zyroofficial.hud.ZyroHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Move the profit box, win streak box, auction board and popup with arrow buttons. */
public class HudEditorScreen extends Screen {
	private static final String[] NAMES = {"Profit", "Streak", "Auction", "Popup"};
	private static final int[] STEPS = {1, 5, 10, 25};

	private static int selected;
	private static int stepIndex = 1;

	private final Screen parent;
	private Button stepButton;
	private final Button[] selectors = new Button[NAMES.length];

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Move HUDs"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ZyroHud.editing = true;
		int cx = this.width / 2;

		int selW = 70;
		int startX = cx - (NAMES.length * selW + (NAMES.length - 1) * 4) / 2;
		for (int i = 0; i < NAMES.length; i++) {
			final int index = i;
			this.selectors[i] = this.addRenderableWidget(Button.builder(Component.literal(NAMES[i]), button -> {
						selected = index;
						refreshSelectors();
					})
					.bounds(startX + i * (selW + 4), 6, selW, 20).build());
		}
		refreshSelectors();

		this.addRenderableWidget(Button.builder(Component.literal("^"), button -> move(0, -1))
				.bounds(cx - 10, this.height - 76, 20, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("<"), button -> move(-1, 0))
				.bounds(cx - 34, this.height - 52, 20, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal(">"), button -> move(1, 0))
				.bounds(cx + 14, this.height - 52, 20, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("v"), button -> move(0, 1))
				.bounds(cx - 10, this.height - 28, 20, 20).build());

		this.stepButton = this.addRenderableWidget(Button.builder(stepLabel(), button -> {
					stepIndex = (stepIndex + 1) % STEPS.length;
					button.setMessage(stepLabel());
				})
				.bounds(cx - 130, this.height - 52, 90, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Reset positions"), button -> resetPositions())
				.bounds(cx - 130, this.height - 28, 90, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Test popup"), button -> PopupManager.test())
				.bounds(cx + 44, this.height - 52, 86, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
				.bounds(cx + 44, this.height - 28, 86, 20).build());
	}

	private void refreshSelectors() {
		for (int i = 0; i < this.selectors.length; i++) {
			this.selectors[i].active = i != selected;
		}
	}

	private static Component stepLabel() {
		return Component.literal("Step: " + STEPS[stepIndex] + "px");
	}

	private void move(int dx, int dy) {
		ZyroConfig config = ZyroConfig.get();
		int step = STEPS[stepIndex];
		switch (selected) {
			case 0:
				config.hudX = Math.max(0, config.hudX + dx * step);
				config.hudY = Math.max(0, config.hudY + dy * step);
				break;
			case 1:
				config.streakX = Math.max(0, config.streakX + dx * step);
				config.streakY = Math.max(0, config.streakY + dy * step);
				break;
			case 2:
				config.auctionX = Math.max(0, config.auctionX + dx * step);
				config.auctionY = Math.max(0, config.auctionY + dy * step);
				break;
			default:
				config.popupX += dx * step;
				config.popupY = Math.max(0, config.popupY + dy * step);
				break;
		}
		ZyroConfig.save();
	}

	private void resetPositions() {
		ZyroConfig config = ZyroConfig.get();
		config.hudX = 6;
		config.hudY = 6;
		config.streakX = 6;
		config.streakY = 60;
		config.auctionX = 6;
		config.auctionY = 100;
		config.popupX = 0;
		config.popupY = 36;
		ZyroConfig.save();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		ZyroConfig config = ZyroConfig.get();
		int[] bounds;
		String info;
		switch (selected) {
			case 0:
				bounds = ZyroHud.profitBounds(this.font);
				info = "Profit box at " + config.hudX + ", " + config.hudY;
				break;
			case 1:
				bounds = ZyroHud.streakBounds(this.font);
				info = "Streak box at " + config.streakX + ", " + config.streakY
						+ (config.streakSeparate ? "" : "  (turn on 'Separate box' in the Streak tab to see it)");
				break;
			case 2:
				bounds = ZyroHud.auctionBounds(this.font);
				info = "Auction board at " + config.auctionX + ", " + config.auctionY;
				break;
			default:
				bounds = new int[] {this.width / 2 + config.popupX - 65, config.popupY, 130, 36};
				info = "Popup offset " + config.popupX + ", " + config.popupY + "  (press Test popup)";
				break;
		}
		Gfx.outline(graphics, bounds[0] - 2, bounds[1] - 2, bounds[2] + 4, bounds[3] + 4, 1, 0xFFFFFFFF);

		Ui.centered(graphics, this.font, Ui.clip(this.font, info, this.width - 20), this.width / 2, 32, Ui.TEXT);
		Ui.centered(graphics, this.font, "Pick a box, then use the arrows to move it.", this.width / 2, 44, Ui.MUTED);
	}

	@Override
	public void onClose() {
		ZyroHud.editing = false;
		this.minecraft.gui.setScreen(this.parent);
	}
}
