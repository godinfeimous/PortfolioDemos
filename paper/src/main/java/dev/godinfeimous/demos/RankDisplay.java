package dev.godinfeimous.demos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Prefijo del rango en la lista del tab, siempre al día.
 * LuckPerms recalcula los datos de un usuario cuando cambian sus grupos o permisos (desde la consola,
 * desde la web o desde otro servidor de la red) y avisa por su bus de eventos, en sus propios hilos:
 * el cambio se aplica en el scheduler del jugador.
 */
final class RankDisplay implements Listener {

	private final JavaPlugin plugin;
	private final LuckPerms luckPerms;

	RankDisplay(JavaPlugin plugin, LuckPerms luckPerms) {
		this.plugin = plugin;
		this.luckPerms = luckPerms;
	}

	void register() {
		luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
			Player player = plugin.getServer().getPlayer(event.getUser().getUniqueId());
			if (player != null) player.getScheduler().run(plugin, task -> apply(player, event.getUser()), null);
		});
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		apply(event.getPlayer(), luckPerms.getPlayerAdapter(Player.class).getUser(event.getPlayer()));
	}

	/** Los prefijos se guardan en MiniMessage (p. ej. "<red>[Owner] </red>"). */
	private static void apply(Player player, User user) {
		String prefix = user.getCachedData().getMetaData().getPrefix();
		Component name = MiniMessage.miniMessage().deserialize(prefix == null ? "" : prefix).append(Component.text(player.getName()));
		player.playerListName(name);
	}
}
