package dev.godinfeimous.demos;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.ImmutableContextSet;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Contexto "region" para LuckPerms a partir de WorldGuard: permisos que cambian según dónde estás.
 *   /lp group vip permission set essentials.fly true region=spawn
 *
 * LuckPerms llama a calculate() muy a menudo y desde cualquier hilo, así que ahí no se consulta WorldGuard:
 * las regiones de cada jugador se calculan en su hilo al cambiar de bloque, se guardan en caché y solo
 * cuando cambian se avisa a LuckPerms (signalContextUpdate) para que recalcule sus permisos.
 */
final class RegionContext implements ContextCalculator<Player>, Listener {

	private static final String KEY = "region";

	private final LuckPerms luckPerms;
	private final Map<UUID, Set<String>> regions = new ConcurrentHashMap<>();

	RegionContext(LuckPerms luckPerms) {
		this.luckPerms = luckPerms;
	}

	@Override
	public void calculate(Player target, ContextConsumer consumer) {
		for (String region : regions.getOrDefault(target.getUniqueId(), Set.of())) consumer.accept(KEY, region);
	}

	/** Para el autocompletado del editor web y de /lp: todas las regiones que existen. */
	@Override
	public ContextSet estimatePotentialContexts() {
		ImmutableContextSet.Builder builder = ImmutableContextSet.builder();
		RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
		for (World world : org.bukkit.Bukkit.getWorlds()) {
			RegionManager manager = container.get(BukkitAdapter.adapt(world));
			if (manager != null) manager.getRegions().keySet().forEach(id -> builder.add(KEY, id));
		}
		return builder.build();
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onMove(PlayerMoveEvent event) {
		if (!event.hasChangedBlock()) return; // girar la cámara no cambia de región
		refresh(event.getPlayer(), event.getTo());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		regions.remove(event.getPlayer().getUniqueId());
	}

	private void refresh(Player player, Location location) {
		Set<String> now = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
				.getApplicableRegions(BukkitAdapter.adapt(location)).getRegions().stream()
				.map(ProtectedRegion::getId).collect(Collectors.toUnmodifiableSet());
		Set<String> before = regions.put(player.getUniqueId(), now);
		if (!now.equals(before)) luckPerms.getContextManager().signalContextUpdate(player);
	}
}
