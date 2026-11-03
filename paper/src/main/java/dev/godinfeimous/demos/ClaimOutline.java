package dev.godinfeimous.demos;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * `/borde`: dibuja el borde de la protección en la que estás con BlockDisplay de cristal.
 * - Solo los ve quien usa el comando (setVisibleByDefault(false) + showEntity): no ensucia la pantalla de nadie más.
 * - Nacen con escala 0 y crecen con interpolación en el cliente, escalonados alrededor del borde: una animación
 *   fluida sin mandar un paquete por fotograma.
 * - No se guardan en el mundo (setPersistent(false)) y se borran solas a los 10 s.
 */
final class ClaimOutline {

	private static final int MAX_DISPLAYS = 240;
	private static final Transformation HIDDEN = new Transformation(new Vector3f(0.5f, 0.5f, 0.5f), new AxisAngle4f(), new Vector3f(0f), new AxisAngle4f());
	private static final Transformation SHOWN = new Transformation(new Vector3f(0.05f, 0f, 0.05f), new AxisAngle4f(), new Vector3f(0.9f, 1f, 0.9f), new AxisAngle4f());

	private ClaimOutline() {}

	static LiteralCommandNode<CommandSourceStack> command(JavaPlugin plugin) {
		return Commands.literal("borde")
				.executes(context -> {
					if (!(context.getSource().getExecutor() instanceof Player player)) return 0;
					Optional<ProtectedRegion> region = regionAt(player.getLocation());
					if (region.isEmpty()) {
						player.sendRichMessage("<gray>Aquí no hay ninguna protección.");
						return 0;
					}
					show(plugin, player, region.get());
					return Command.SINGLE_SUCCESS;
				})
				.build();
	}

	/** La región de más prioridad en ese punto (la que manda si hay varias encimadas). */
	private static Optional<ProtectedRegion> regionAt(Location location) {
		return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
				.getApplicableRegions(BukkitAdapter.adapt(location)).getRegions().stream()
				.max(Comparator.comparingInt(ProtectedRegion::getPriority));
	}

	private static void show(JavaPlugin plugin, Player player, ProtectedRegion region) {
		BlockVector3 min = region.getMinimumPoint(), max = region.getMaximumPoint();
		List<int[]> border = perimeter(min.x(), min.z(), max.x(), max.z());
		int step = Math.max(1, border.size() / MAX_DISPLAYS); // las protecciones grandes se dibujan con huecos
		World world = player.getWorld();
		int y = player.getLocation().getBlockY();
		for (int i = 0; i < border.size(); i += step) {
			int[] xz = border.get(i);
			int delay = i / step / 3; // escalonado: el borde "se dibuja" alrededor del jugador
			BlockDisplay display = world.spawn(new Location(world, xz[0], y, xz[1]), BlockDisplay.class, entity -> {
				entity.setBlock(Material.LIME_STAINED_GLASS.createBlockData());
				entity.setBrightness(new Display.Brightness(15, 15));
				entity.setTransformation(HIDDEN);
				entity.setPersistent(false);
				entity.setVisibleByDefault(false);
			});
			player.showEntity(plugin, display);
			display.getScheduler().runDelayed(plugin, task -> {
				display.setInterpolationDelay(0);
				display.setInterpolationDuration(6);
				display.setTransformation(SHOWN);
			}, null, 2L + delay);
			display.getScheduler().runDelayed(plugin, task -> display.remove(), null, 200L + delay);
		}
	}

	/** Bloques del contorno de un rectángulo, recorridos en orden (para que la animación dé la vuelta). */
	static List<int[]> perimeter(int x0, int z0, int x1, int z1) {
		List<int[]> blocks = new ArrayList<>();
		for (int x = x0; x <= x1; x++) blocks.add(new int[] {x, z0});
		for (int z = z0 + 1; z <= z1; z++) blocks.add(new int[] {x1, z});
		for (int x = x1 - 1; x >= x0 && z1 > z0; x--) blocks.add(new int[] {x, z1});
		for (int z = z1 - 1; z > z0 && x1 > x0; z--) blocks.add(new int[] {x0, z});
		return blocks;
	}
}
