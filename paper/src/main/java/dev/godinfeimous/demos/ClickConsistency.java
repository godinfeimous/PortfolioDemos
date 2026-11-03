package dev.godinfeimous.demos;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Chequeo de autoclicker a nivel de paquetes (PacketEvents): mira los golpes al aire (ANIMATION) y mide
 * cuánto varía el tiempo entre clics. Una mano humana nunca es tan constante como un macro.
 * - Corre en el hilo de red: estructuras concurrentes y nada de API de Bukkit aquí.
 * - Mientras el jugador pica un bloque se ignoran los golpes (picar también los manda).
 * - Solo alerta al staff con demo.alerts: el castigo lo decide una persona.
 */
final class ClickConsistency implements PacketListener {

	private static final int WINDOW = 20;               // últimos 20 clics
	private static final double MIN_CPS = 9;            // con menos no se analiza
	private static final double MAX_DEVIATION_MS = 8;   // más constante que esto no es humano

	private final JavaPlugin plugin;
	private final Map<UUID, ArrayDeque<Long>> clicks = new ConcurrentHashMap<>();
	private final Set<UUID> digging = ConcurrentHashMap.newKeySet();

	ClickConsistency(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		UUID id = event.getUser().getUUID();
		if (id == null) return;
		if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
			DiggingAction action = new WrapperPlayClientPlayerDigging(event).getAction();
			if (action == DiggingAction.START_DIGGING) digging.add(id); else digging.remove(id);
			return;
		}
		if (event.getPacketType() != PacketType.Play.Client.ANIMATION || digging.contains(id)) return;
		ArrayDeque<Long> window = clicks.computeIfAbsent(id, key -> new ArrayDeque<>());
		synchronized (window) {
			window.addLast(System.nanoTime());
			if (window.size() > WINDOW) window.removeFirst();
			if (window.size() == WINDOW) check(event.getUser().getName(), window);
		}
	}

	@Override
	public void onUserDisconnect(UserDisconnectEvent event) {
		UUID id = event.getUser().getUUID();
		if (id == null) return;
		clicks.remove(id);
		digging.remove(id);
	}

	private void check(String name, ArrayDeque<Long> window) {
		double[] gaps = new double[WINDOW - 1];
		Long previous = null;
		int i = 0;
		for (long time : window) {
			if (previous != null) gaps[i++] = (time - previous) / 1_000_000.0;
			previous = time;
		}
		double mean = 0;
		for (double gap : gaps) mean += gap / gaps.length;
		double variance = 0;
		for (double gap : gaps) variance += (gap - mean) * (gap - mean) / gaps.length;
		double cps = 1000 / mean, deviation = Math.sqrt(variance);
		if (cps < MIN_CPS || deviation > MAX_DEVIATION_MS) return;
		window.clear();

		Component alert = MiniMessage.miniMessage().deserialize(
				"<gold>[Demo AC]</gold> <white><player></white> <gray>hace <aqua><cps></aqua> CPS con ±<aqua><dev></aqua> ms de variación",
				Placeholder.unparsed("player", name),
				Placeholder.unparsed("cps", String.format(Locale.ROOT, "%.1f", cps)),
				Placeholder.unparsed("dev", String.format(Locale.ROOT, "%.1f", deviation)));
		plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> plugin.getServer().broadcast(alert, "demo.alerts"));
	}
}
