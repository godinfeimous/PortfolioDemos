package dev.godinfeimous.demos;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Flag propio de WorldGuard: `/rg flag spawn vein-mining deny` apaga el pico de vena en esa región.
 * Se consulta como cualquier flag: con herencia de regiones, prioridades y el bypass de los admins.
 */
final class VeinFlag {

	private static StateFlag flag;

	private VeinFlag() {}

	static void register(Logger log) {
		FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
		try {
			StateFlag created = new StateFlag("vein-mining", true);
			registry.register(created);
			flag = created;
		} catch (FlagConflictException e) {
			// Otro plugin ya lo registró: se reutiliza si es del mismo tipo.
			Flag<?> existing = registry.get("vein-mining");
			if (existing instanceof StateFlag state) flag = state;
			else log.warning("El flag vein-mining ya existe con otro tipo: el pico de vena no respetará regiones.");
		}
	}

	static boolean allows(Player player, Location location) {
		if (flag == null) return true;
		LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
		if (WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(local, local.getWorld())) return true;
		RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
		return query.testState(BukkitAdapter.adapt(location), local, flag);
	}
}
