package dev.godinfeimous.demos.minestom;

import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;

/**
 * Servidor de minijuegos con Minestom: sin código de vanilla, solo lo que el juego necesita.
 * Arranca en segundos, gasta poca RAM y cada partida es una copia ligera del mapa: ideal para tener
 * decenas de arenas en un solo proceso detrás de Velocity.
 */
public final class MinigameServer {

	private MinigameServer() {}

	public static void main(String[] args) {
		// Detrás de Velocity con reenvío moderno; sin el secreto, modo online normal (para probar en local).
		String secret = System.getenv("VELOCITY_SECRET");
		MinecraftServer server = MinecraftServer.init(secret == null ? new Auth.Online() : new Auth.Velocity(secret));

		InstanceContainer map = MinecraftServer.getInstanceManager().createInstanceContainer();
		map.setChunkSupplier(LightingChunk::new);
		map.setGenerator(unit -> {
			unit.modifier().fillHeight(0, 39, Block.STONE);
			unit.modifier().fillHeight(39, 40, Block.SMOOTH_STONE);
		});

		Arenas arenas = new Arenas(map);
		MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
			event.setSpawningInstance(arenas.join(event.getPlayer()).instance());
			event.getPlayer().setRespawnPoint(new Pos(0.5, 40, 0.5));
		});

		server.start("0.0.0.0", 25565);
	}
}
