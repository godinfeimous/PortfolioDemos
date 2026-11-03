package dev.godinfeimous.demos;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Pico de vena: al romper un mineral con él, cae toda la vena conectada.
 * - El ítem se marca con un PersistentDataContainer (no por nombre ni lore, que el jugador puede copiar).
 * - Cada bloque se rompe con player.breakBlock(): WorldGuard, CoreProtect o un anticheat lo ven como uno normal.
 * - La vena se rompe repartida entre ticks en el scheduler del jugador (compatible con Folia): no hay picos de lag.
 */
final class VeinMiner implements Listener {

	private static final int LIMIT = 64;    // bloques por vena
	private static final int PER_TICK = 8;  // bloques rotos por tick
	private static final List<Tag<Material>> ORES = List.of(Tag.COAL_ORES, Tag.IRON_ORES, Tag.COPPER_ORES, Tag.GOLD_ORES,
			Tag.REDSTONE_ORES, Tag.LAPIS_ORES, Tag.DIAMOND_ORES, Tag.EMERALD_ORES);

	private final JavaPlugin plugin;
	private final StatsRepository stats;
	private final NamespacedKey key;
	/** Jugadores con una vena en curso: los bloques que rompe el propio plugin no vuelven a empezar el proceso. */
	private final Set<UUID> mining = ConcurrentHashMap.newKeySet();

	VeinMiner(JavaPlugin plugin, StatsRepository stats) {
		this.plugin = plugin;
		this.stats = stats;
		this.key = new NamespacedKey(plugin, "vein");
	}

	ItemStack createTool() {
		ItemStack tool = ItemStack.of(Material.DIAMOND_PICKAXE);
		tool.editMeta(meta -> {
			meta.displayName(MiniMessage.miniMessage().deserialize("<!italic><gradient:#55ffff:#ff55ff>Pico de vena</gradient>"));
			meta.lore(List.of(MiniMessage.miniMessage().deserialize("<!italic><gray>Rompe la vena entera de mineral")));
			meta.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
		});
		return tool;
	}

	LiteralCommandNode<CommandSourceStack> command() {
		return Commands.literal("vena")
				.requires(source -> source.getSender().hasPermission("demo.vena"))
				.executes(context -> {
					if (!(context.getSource().getExecutor() instanceof Player player)) return 0;
					player.getInventory().addItem(createTool());
					return Command.SINGLE_SUCCESS;
				})
				.build();
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onBreak(BlockBreakEvent event) {
		Player player = event.getPlayer();
		Block origin = event.getBlock();
		Material type = origin.getType();
		if (mining.contains(player.getUniqueId()) || ORES.stream().noneMatch(tag -> tag.isTagged(type))) return;
		ItemStack tool = player.getInventory().getItemInMainHand();
		if (!tool.hasItemMeta() || !tool.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN)) return;
		if (!VeinFlag.allows(player, origin.getLocation())) return;

		List<Block> vein = collect(origin, type);
		if (vein.isEmpty()) return;
		mining.add(player.getUniqueId());
		breakGradually(player, vein, type);
	}

	/** Recorrido en anchura por los 26 vecinos: el mismo mineral, hasta LIMIT bloques. */
	private static List<Block> collect(Block origin, Material type) {
		ArrayDeque<Block> queue = new ArrayDeque<>(List.of(origin));
		Set<Block> seen = new HashSet<>(Set.of(origin));
		List<Block> found = new ArrayList<>();
		while (!queue.isEmpty() && found.size() < LIMIT) {
			Block block = queue.poll();
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
				Block next = block.getRelative(dx, dy, dz);
				if (found.size() < LIMIT && next.getType() == type && seen.add(next)) {
					queue.add(next);
					found.add(next);
				}
			}
		}
		return found;
	}

	private void breakGradually(Player player, List<Block> vein, Material type) {
		Iterator<Block> blocks = vein.iterator();
		int[] broken = {1}; // el bloque original ya lo rompió el jugador
		player.getScheduler().runAtFixedRate(plugin, task -> {
			for (int i = 0; i < PER_TICK && blocks.hasNext(); i++) {
				Block block = blocks.next();
				if (block.getType() == type && player.breakBlock(block)) broken[0]++;
			}
			if (!blocks.hasNext() || !player.isOnline()) {
				task.cancel();
				mining.remove(player.getUniqueId());
				stats.addOres(player.getUniqueId(), broken[0]);
			}
		}, () -> mining.remove(player.getUniqueId()), 1L, 1L);
	}
}
