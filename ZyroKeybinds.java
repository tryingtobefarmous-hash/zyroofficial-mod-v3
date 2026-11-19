package com.zyroofficial.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Every Zyroofficial keybind. They all appear under Options > Controls > Key Binds
 * in the "Zyroofficial Mod" category and can be rebound or unbound there.
 */
public final class ZyroKeybinds {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("zyroofficial", "main")
	);

	public static final KeyMapping OPEN_MENU = register("open_menu", InputConstants.KEY_RSHIFT);
	public static final KeyMapping TOGGLE_HUD = register("toggle_hud", InputConstants.KEY_H);
	public static final KeyMapping OPEN_AUCTION = register("open_auction", InputConstants.KEY_O);
	public static final KeyMapping OPEN_MONEY = register("open_money", InputConstants.KEY_M);
	public static final KeyMapping MOVE_HUD = register("move_hud", InputConstants.KEY_J);
	/** Comma key (GLFW 44): adds 1 to the win streak. */
	public static final KeyMapping WIN_PLUS = register("win_plus", 44);
	/** Period key (GLFW 46): resets the win streak to 0. */
	public static final KeyMapping STREAK_RESET = register("streak_reset", 46);

	private ZyroKeybinds() {
	}

	private static KeyMapping register(String name, int defaultKey) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.zyroofficial." + name,
				InputConstants.Type.KEYSYM,
				defaultKey,
				CATEGORY
		));
	}

	/** Forces the class to load so every keybind registers during client startup. */
	public static void init() {
		// Static initialisation does the work.
	}
}
