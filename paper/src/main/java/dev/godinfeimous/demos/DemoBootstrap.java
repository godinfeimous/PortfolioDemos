package dev.godinfeimous.demos;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;

/**
 * Bootstrap de Paper: corre ANTES de que el servidor cargue los registros del juego.
 * Aquí se añade un encantamiento de verdad (no un lore falso): sale en la mesa de encantamientos y en el yunque,
 * se combina con libros y el cliente lo muestra con su nombre, igual que los del juego.
 */
public final class DemoBootstrap implements PluginBootstrap {

	public static final TypedKey<Enchantment> REPLANT = TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("godinfeimous", "replantar"));

	@Override
	public void bootstrap(BootstrapContext context) {
		context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose().newHandler(event ->
				event.registry().register(REPLANT, builder -> builder
						.description(Component.text("Replantar", NamedTextColor.GREEN))
						.supportedItems(event.getOrCreateTag(ItemTypeTagKeys.HOES))
						.weight(5)
						.maxLevel(1)
						.anvilCost(4)
						.minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(15, 0))
						.maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(65, 0))
						.activeSlots(EquipmentSlotGroup.MAINHAND))));
	}
}
