package dev.godinfeimous.demos;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.luckperms.api.LuckPerms;
import java.io.IOException;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin de demostración para el portfolio: junta en un solo jar las piezas que suelo programar
 * para servidores (Paper 26.2): ítems con datos propios, base de datos asíncrona, flags de WorldGuard,
 * rangos con la API de LuckPerms, un chequeo a nivel de paquetes y menús nativos para Bedrock.
 */
public final class DemoPlugin extends JavaPlugin {

	private StatsRepository stats;
	private PlayerDataHandoff handoff;
	private MetricsExporter metrics;
	private RegionContext regionContext;
	private LuckPerms luckPerms;

	@Override
	public void onLoad() {
		// Los flags de WorldGuard solo se pueden registrar antes de que WorldGuard termine de cargar.
		VeinFlag.register(getLogger());
	}

	@Override
	public void onEnable() {
		saveDefaultConfig();
		stats = new StatsRepository(
				getConfig().getString("database.url", "jdbc:mariadb://localhost:3306/demo"),
				getConfig().getString("database.user", "demo"),
				getConfig().getString("database.password", ""));

		VeinMiner veins = new VeinMiner(this, stats);
		getServer().getPluginManager().registerEvents(veins, this);
		getServer().getPluginManager().registerEvents(new BedrockMenu.Clicks(this), this);

		handoff = new PlayerDataHandoff(this, getConfig().getString("redis.uri", "redis://localhost:6379"), getConfig().getString("server-id", "survival-1"), stats.dataSource());
		getServer().getPluginManager().registerEvents(handoff, this);
		try {
			metrics = new MetricsExporter(this, getConfig().getInt("metrics.port", 9225));
		} catch (IOException e) {
			getSLF4JLogger().warn("No se pudo abrir el puerto de métricas", e);
		}

		luckPerms = getServer().getServicesManager().load(LuckPerms.class);
		if (luckPerms != null) {
			new RankDisplay(this, luckPerms).register();
			regionContext = new RegionContext(luckPerms);
			luckPerms.getContextManager().registerCalculator(regionContext);
			getServer().getPluginManager().registerEvents(regionContext, this);
		}

		PacketEvents.getAPI().getEventManager().registerListener(new ClickConsistency(this), PacketListenerPriority.MONITOR);
		PacketEvents.getAPI().getEventManager().registerListener(new LagCompensatedReach(this), PacketListenerPriority.MONITOR);

		// Comandos con Brigadier (API de Paper): autocompletado y validación de argumentos en el cliente.
		getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
			event.registrar().register(veins.command(), "Da el pico de vena");
			event.registrar().register(BedrockMenu.command(this), "Abre el menú del servidor");
			event.registrar().register(ClaimOutline.command(this), "Muestra el borde de la protección en la que estás");
			if (luckPerms != null) event.registrar().register(Promotions.command(luckPerms), "Sube a un jugador en el track de staff");
		});
	}

	@Override
	public void onDisable() {
		if (handoff != null) handoff.close();
		if (metrics != null) metrics.close();
		if (luckPerms != null && regionContext != null) luckPerms.getContextManager().unregisterCalculator(regionContext);
		if (stats != null) stats.close();
	}
}
