package dev.godinfeimous.demos.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Zoom con la C: mientras se mantiene, el FOV baja suavemente; la rueda del ratón ajusta cuánto.
 * El suavizado es exponencial y usa el tiempo real entre fotogramas: se ve igual a 30 que a 240 FPS.
 */
public final class Zoom {

	private static KeyMapping key;
	private static double level = 4;      // aumento elegido con la rueda (x2 a x12)
	private static double current = 1;    // multiplicador de FOV que se ve ahora
	private static long lastFrame = System.nanoTime();

	private Zoom() {}

	static void register() {
		key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.godinfeimous.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, KeyMapping.Category.MISC));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!key.isDown()) level = 4; // al soltar, vuelve al aumento por defecto
		});
	}

	public static boolean active() {
		return key != null && key.isDown();
	}

	/** La rueda cambia el aumento mientras se hace zoom (y no cambia de ranura). */
	public static void scroll(double amount) {
		level = Math.clamp(level * (amount > 0 ? 1.25 : 0.8), 2, 12);
	}

	public static float apply(float fov) {
		long now = System.nanoTime();
		double dt = Math.min(0.1, (now - lastFrame) / 1e9);
		lastFrame = now;
		double target = active() ? 1 / level : 1;
		current += (target - current) * (1 - Math.exp(-dt * 14));
		return (float) (fov * current);
	}
}
