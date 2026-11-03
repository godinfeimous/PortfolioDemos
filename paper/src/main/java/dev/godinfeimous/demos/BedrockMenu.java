package dev.godinfeimous.demos;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;

/**
 * `/menu` con dos interfaces: los jugadores de Bedrock (vía Geyser) reciben un formulario nativo, pensado para
 * el dedo; los de Java, un cofre. Las dos llevan a la misma acción.
 */
final class BedrockMenu {

	private static final String[] OPTIONS = {"Spawn", "Tienda"};

	private BedrockMenu() {}

	static LiteralCommandNode<CommandSourceStack> command(JavaPlugin plugin) {
		return Commands.literal("menu")
				.executes(context -> {
					if (!(context.getSource().getExecutor() instanceof Player player)) return 0;
					open(plugin, player);
					return Command.SINGLE_SUCCESS;
				})
				.build();
	}

	static void open(JavaPlugin plugin, Player player) {
		GeyserConnection bedrock = GeyserApi.api().connectionByUuid(player.getUniqueId());
		if (bedrock != null) {
			bedrock.sendForm(SimpleForm.builder()
					.title("Menú del servidor")
					.content("¿A dónde vas?")
					.button(OPTIONS[0], FormImage.Type.PATH, "textures/items/compass_item")
					.button(OPTIONS[1], FormImage.Type.PATH, "textures/items/emerald")
					// La respuesta llega por la red de Geyser: se vuelve al hilo del jugador antes de tocar el mundo.
					.validResultHandler(response -> player.getScheduler().run(plugin, task -> choose(player, response.clickedButtonId()), null)));
			return;
		}
		Menu menu = new Menu(plugin);
		menu.inventory.setItem(3, icon(Material.COMPASS, OPTIONS[0]));
		menu.inventory.setItem(5, icon(Material.EMERALD, OPTIONS[1]));
		player.openInventory(menu.inventory);
	}

	private static ItemStack icon(Material material, String name) {
		ItemStack item = ItemStack.of(material);
		item.editMeta(meta -> meta.displayName(Component.text(name, NamedTextColor.AQUA)));
		return item;
	}

	private static void choose(Player player, int option) {
		if (option == 0) player.teleportAsync(player.getWorld().getSpawnLocation());
		else player.performCommand("shop");
	}

	/** El cofre se reconoce por su holder, no por el título (que cualquier otro inventario podría repetir). */
	private static final class Menu implements InventoryHolder {
		private final Inventory inventory;

		Menu(JavaPlugin plugin) {
			this.inventory = plugin.getServer().createInventory(this, 9, Component.text("Menú del servidor"));
		}

		@Override
		public Inventory getInventory() {
			return inventory;
		}
	}

	static final class Clicks implements Listener {
		private final JavaPlugin plugin;

		Clicks(JavaPlugin plugin) {
			this.plugin = plugin;
		}

		@EventHandler
		public void onClick(InventoryClickEvent event) {
			if (!(event.getInventory().getHolder(false) instanceof Menu) || !(event.getWhoClicked() instanceof Player player)) return;
			event.setCancelled(true);
			int option = event.getRawSlot() == 3 ? 0 : event.getRawSlot() == 5 ? 1 : -1;
			if (option < 0) return;
			player.closeInventory();
			player.getScheduler().run(plugin, task -> choose(player, option), null);
		}
	}
}
