package com.zyroofficial.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Things that touch Minecraft internals whose names may differ between versions (sounds, sending chat).
 * They use reflection so a rename can never break the build: if something is missing, the call just
 * returns false and the mod keeps working.
 */
public final class Platform {
	public static final String[] SOUND_NAMES = {"Level Up", "XP Orb", "Pling", "Chime", "Click", "Bell"};

	private static final String[][] SOUND_FIELDS = {
			{"PLAYER_LEVELUP"},
			{"EXPERIENCE_ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_PICKUP"},
			{"NOTE_BLOCK_PLING", "BLOCK_NOTE_BLOCK_PLING"},
			{"AMETHYST_BLOCK_CHIME", "BLOCK_AMETHYST_BLOCK_CHIME"},
			{"UI_BUTTON_CLICK"},
			{"NOTE_BLOCK_BELL", "BLOCK_NOTE_BLOCK_BELL"}
	};

	private Platform() {
	}

	/** Shows a message only to you, in your chat. */
	public static void local(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null) {
			return;
		}
		mc.player.sendSystemMessage(Component.literal(message));
	}

	/** Plays a UI sound. Returns false if the sound could not be played. */
	public static boolean playSound(int index, float volume, float pitch) {
		try {
			int i = Math.max(0, Math.min(SOUND_FIELDS.length - 1, index));
			Class<?> events = Class.forName("net.minecraft.sounds.SoundEvents");
			Object sound = null;
			for (String name : SOUND_FIELDS[i]) {
				try {
					Field field = events.getField(name);
					sound = field.get(null);
					if (sound != null) {
						break;
					}
				} catch (ReflectiveOperationException ignored) {
					// Try the next candidate name.
				}
			}
			if (sound == null) {
				return false;
			}

			Class<?> simple = Class.forName("net.minecraft.client.resources.sounds.SimpleSoundInstance");
			Object instance = null;
			for (Method method : simple.getMethods()) {
				if (!method.getName().equals("forUI")) {
					continue;
				}
				Class<?>[] p = method.getParameterTypes();
				if (p.length == 3 && p[0].isInstance(sound) && p[1] == float.class && p[2] == float.class) {
					instance = method.invoke(null, sound, pitch, volume);
					break;
				}
			}
			if (instance == null) {
				for (Method method : simple.getMethods()) {
					if (!method.getName().equals("forUI")) {
						continue;
					}
					Class<?>[] p = method.getParameterTypes();
					if (p.length == 2 && p[0].isInstance(sound) && p[1] == float.class) {
						instance = method.invoke(null, sound, pitch);
						break;
					}
				}
			}
			if (instance == null) {
				return false;
			}

			Minecraft mc = Minecraft.getInstance();
			Object manager = mc.getClass().getMethod("getSoundManager").invoke(mc);
			for (Method method : manager.getClass().getMethods()) {
				if (method.getName().equals("play") && method.getParameterCount() == 1
						&& method.getParameterTypes()[0].isInstance(instance)) {
					method.invoke(manager, instance);
					return true;
				}
			}
			return false;
		} catch (Throwable t) {
			return false;
		}
	}

	/** Sends a normal chat message as you. Returns false if it could not be sent. */
	public static boolean sendChat(String message) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || mc.player == null || message == null || message.isBlank()) {
				return false;
			}
			Object player = mc.player;
			Object connection = null;
			try {
				Field field = player.getClass().getField("connection");
				connection = field.get(player);
			} catch (ReflectiveOperationException ignored) {
				// Fall through to the next lookup.
			}
			if (connection == null) {
				try {
					connection = mc.getClass().getMethod("getConnection").invoke(mc);
				} catch (ReflectiveOperationException ignored) {
					// No connection available.
				}
			}
			if (connection == null) {
				return false;
			}
			Method send = connection.getClass().getMethod("sendChat", String.class);
			send.invoke(connection, message);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}
}
