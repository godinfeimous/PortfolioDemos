package dev.godinfeimous.demos.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

/** Zoom con la C y comando de cliente `/menciones` (activa o desactiva el resaltado sin tocar el servidor). */
public final class DemoClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		Zoom.register();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("menciones").executes(context -> {
					boolean on = Mentions.toggle();
					context.getSource().sendFeedback(Component.literal(on ? "Menciones resaltadas: sí" : "Menciones resaltadas: no"));
					return 1;
				})));
	}
}
