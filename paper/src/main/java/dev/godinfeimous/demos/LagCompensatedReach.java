package dev.godinfeimous.demos;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reach con compensación de lag, la técnica de los anticheats por predicción:
 * 1. Cada tick se guarda dónde está cada jugador (historial de 2 s).
 * 2. El ping REAL de cada uno se mide con transacciones: un paquete Ping con id negativo (los positivos los puede usar
 *    el propio juego) y su Pong de respuesta. No es el ping del tab, que es una media suavizada.
 * 3. Cuando A golpea a B, se mira dónde estaba B en el instante que A veía en su pantalla
 *    (ahora - ping de A - interpolación del cliente) y se mide del ojo de A a la caja de B en ese momento.
 * Un golpe largo suelto no alerta: hacen falta varios seguidos (buffer). El castigo lo decide el staff.
 */
final class LagCompensatedReach implements PacketListener {

	private static final int HISTORY = 40;                       // ticks guardados (2 s)
	private static final long TICK_NANOS = 50_000_000L;
	private static final long CLIENT_INTERPOLATION = 3 * TICK_NANOS; // el cliente dibuja a los demás ~3 ticks tarde
	private static final double MAX_REACH = 3.0, MARGIN = 0.1, EYE_HEIGHT = 1.62;
	private static final int ALERT_AFTER = 3;

	/** Posiciones de un jugador en el tiempo (anillo de HISTORY muestras). */
	private static final class Track {
		private final long[] times = new long[HISTORY];
		private final double[][] positions = new double[HISTORY][3];
		private int head;

		synchronized void push(long time, Location location) {
			head = (head + 1) % HISTORY;
			times[head] = time;
			positions[head][0] = location.getX();
			positions[head][1] = location.getY();
			positions[head][2] = location.getZ();
		}

		/** La última muestra tomada antes de `time` (o la más vieja que haya). */
		synchronized double[] at(long time) {
			for (int i = 0; i < HISTORY; i++) {
				int index = Math.floorMod(head - i, HISTORY);
				if (times[index] != 0 && times[index] <= time) return positions[index].clone();
			}
			return positions[Math.floorMod(head + 1, HISTORY)].clone();
		}

		synchronized double[] latest() { return positions[head].clone(); }
	}

	private final JavaPlugin plugin;
	private final Map<UUID, Track> tracks = new ConcurrentHashMap<>();
	private final Map<Integer, UUID> byEntityId = new ConcurrentHashMap<>();
	private final Map<UUID, Map<Integer, Long>> pings = new ConcurrentHashMap<>();
	private final Map<UUID, Long> roundTrip = new ConcurrentHashMap<>();
	private final Map<UUID, Integer> buffer = new ConcurrentHashMap<>();
	private final AtomicInteger nextPing = new AtomicInteger(-1);
	private int ticks;

	LagCompensatedReach(JavaPlugin plugin) {
		this.plugin = plugin;
		plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> tick(), 1L, 1L);
	}

	private void tick() {
		long now = System.nanoTime();
		boolean sendPings = ++ticks % 10 == 0;
		for (Player player : plugin.getServer().getOnlinePlayers()) {
			UUID id = player.getUniqueId();
			tracks.computeIfAbsent(id, key -> new Track()).push(now, player.getLocation());
			byEntityId.put(player.getEntityId(), id);
			if (!sendPings) continue;
			int ping = nextPing.getAndUpdate(value -> value <= Integer.MIN_VALUE + 1 ? -1 : value - 1);
			pings.computeIfAbsent(id, key -> new ConcurrentHashMap<>()).put(ping, now);
			PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPing(ping));
		}
	}

	@Override
	public void onPacketReceive(PacketReceiveEvent event) {
		UUID attacker = event.getUser().getUUID();
		if (attacker == null) return;
		if (event.getPacketType() == PacketType.Play.Client.PONG) {
			int id = new WrapperPlayClientPong(event).getId();
			Long sent = pings.getOrDefault(attacker, Map.of()).remove(id);
			if (sent != null) roundTrip.put(attacker, System.nanoTime() - sent);
			return;
		}
		if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
		WrapperPlayClientInteractEntity interact = new WrapperPlayClientInteractEntity(event);
		if (interact.getAction() != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;
		UUID victim = byEntityId.get(interact.getEntityId());
		Track attackerTrack = tracks.get(attacker), victimTrack = victim == null ? null : tracks.get(victim);
		if (attackerTrack == null || victimTrack == null) return;

		long seenAt = System.nanoTime() - roundTrip.getOrDefault(attacker, 0L) - CLIENT_INTERPOLATION;
		double[] eye = attackerTrack.latest();
		double distance = distanceToBox(eye[0], eye[1] + EYE_HEIGHT, eye[2], victimTrack.at(seenAt));
		if (distance <= MAX_REACH + MARGIN) {
			buffer.computeIfPresent(attacker, (key, value) -> value > 1 ? value - 1 : null);
			return;
		}
		if (buffer.merge(attacker, 1, Integer::sum) >= ALERT_AFTER) {
			buffer.remove(attacker);
			alert(event.getUser().getName(), distance, roundTrip.getOrDefault(attacker, 0L) / 1_000_000L);
		}
	}

	@Override
	public void onUserDisconnect(UserDisconnectEvent event) {
		UUID id = event.getUser().getUUID();
		if (id == null) return;
		tracks.remove(id);
		pings.remove(id);
		roundTrip.remove(id);
		buffer.remove(id);
		byEntityId.values().removeIf(id::equals);
	}

	/** Distancia del ojo a la caja del jugador golpeado (0,6 × 1,8 bloques, más margen). */
	static double distanceToBox(double x, double y, double z, double[] feet) {
		double half = 0.3 + MARGIN, height = 1.8 + MARGIN;
		double dx = Math.max(Math.max(feet[0] - half - x, 0), x - (feet[0] + half));
		double dy = Math.max(Math.max(feet[1] - y, 0), y - (feet[1] + height));
		double dz = Math.max(Math.max(feet[2] - half - z, 0), z - (feet[2] + half));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private void alert(String name, double distance, long pingMs) {
		var message = MiniMessage.miniMessage().deserialize(
				"<gold>[Demo AC]</gold> <white><player></white> <gray>golpea a <aqua><reach></aqua> bloques con <aqua><ping></aqua> ms reales",
				Placeholder.unparsed("player", name),
				Placeholder.unparsed("reach", String.format(Locale.ROOT, "%.2f", distance)),
				Placeholder.unparsed("ping", Long.toString(pingMs)));
		plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> plugin.getServer().broadcast(message, "demo.alerts"));
	}
}
