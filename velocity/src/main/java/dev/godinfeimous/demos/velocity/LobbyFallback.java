package dev.godinfeimous.demos.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.slf4j.Logger;

/**
 * Red con Velocity: si un servidor de juego se cae o se reinicia, los jugadores pasan al lobby en vez de
 * desconectarse. Un ban o un kick a mano sí los saca: eso no se esconde.
 */
@Plugin(id = "lobby-fallback", name = "LobbyFallback", version = "1.0.0", authors = {"godinfeimous"})
public final class LobbyFallback {

	private final ProxyServer proxy;
	private final Logger logger;

	@Inject
	public LobbyFallback(ProxyServer proxy, Logger logger) {
		this.proxy = proxy;
		this.logger = logger;
	}

	@Subscribe
	public void onKick(KickedFromServerEvent event) {
		if (event.kickedDuringServerConnect() || isPunishment(event.getServerKickReason())) return;
		Optional<RegisteredServer> lobby = proxy.getServer("lobby");
		if (lobby.isEmpty() || event.getServer().equals(lobby.get())) return;
		logger.info("{} salió de {}: va al lobby", event.getPlayer().getUsername(), event.getServer().getServerInfo().getName());
		event.setResult(KickedFromServerEvent.RedirectPlayer.create(lobby.get(),
				Component.text("El servidor se está reiniciando: te llevamos al lobby.", NamedTextColor.YELLOW)));
	}

	private static boolean isPunishment(Optional<Component> reason) {
		String text = reason.map(component -> PlainTextComponentSerializer.plainText().serialize(component).toLowerCase(Locale.ROOT)).orElse("");
		return text.contains("ban") || text.contains("kick");
	}
}
