package dev.godinfeimous.demos.fabric;

import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Menciones en el chat: si una línea nombra al jugador (como palabra entera, no dentro de otro nick),
 * se marca en dorado y suena una vez. La lógica vive aquí; el Mixin solo la engancha al chat.
 */
public final class Mentions {

	private static boolean enabled = true;
	private static String cachedName = "";
	private static Pattern cachedPattern = Pattern.compile("$^");

	private Mentions() {}

	public static boolean toggle() {
		enabled = !enabled;
		return enabled;
	}

	public static Component highlight(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (!enabled || client.player == null) return message;
		String name = client.player.getName().getString();
		if (!name.equals(cachedName)) {
			cachedName = name;
			cachedPattern = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
		}
		if (!cachedPattern.matcher(message.getString()).find()) return message;
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f));
		// Marca delante en vez de recolorear: los colores del propio mensaje (rangos, nicks) se conservan.
		return Component.literal("» ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).append(message);
	}
}
