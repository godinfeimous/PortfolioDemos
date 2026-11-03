package dev.godinfeimous.demos;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import java.io.IOException;
import java.util.Arrays;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Métricas para Prometheus (y paneles/alertas en Grafana): MSPT con percentiles, TPS, jugadores y, por mundo,
 * entidades y chunks cargados. Con esto se ve CUÁNDO empezó el lag y en qué mundo, antes de abrir spark.
 * Se muestrea una vez por segundo en el hilo del servidor; el HTTP lo sirve un hilo aparte del exporter.
 */
final class MetricsExporter {

	private final PrometheusRegistry registry = new PrometheusRegistry();
	private final Gauge mspt = Gauge.builder().name("minecraft_mspt").help("Milisegundos por tick (últimos 100 ticks)").labelNames("quantile").register(registry);
	private final Gauge tps = Gauge.builder().name("minecraft_tps").help("Ticks por segundo, media de 1 minuto").register(registry);
	private final Gauge players = Gauge.builder().name("minecraft_players_online").help("Jugadores conectados").register(registry);
	private final Gauge entities = Gauge.builder().name("minecraft_world_entities").help("Entidades por mundo").labelNames("world").register(registry);
	private final Gauge chunks = Gauge.builder().name("minecraft_world_loaded_chunks").help("Chunks cargados por mundo").labelNames("world").register(registry);
	private final HTTPServer server;
	private final ScheduledTask sampler;

	MetricsExporter(JavaPlugin plugin, int port) throws IOException {
		server = HTTPServer.builder().port(port).registry(registry).buildAndStart();
		sampler = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> sample(plugin), 20L, 20L);
	}

	private void sample(JavaPlugin plugin) {
		// getTickTimes(): duración de los últimos 100 ticks en nanosegundos.
		long[] times = plugin.getServer().getTickTimes().clone();
		Arrays.sort(times);
		mspt.labelValues("0.5").set(times[times.length / 2] / 1e6);
		mspt.labelValues("0.95").set(times[(int) (times.length * 0.95)] / 1e6);
		mspt.labelValues("1").set(times[times.length - 1] / 1e6);
		tps.set(plugin.getServer().getTPS()[0]);
		players.set(plugin.getServer().getOnlinePlayers().size());
		for (World world : plugin.getServer().getWorlds()) {
			entities.labelValues(world.getName()).set(world.getEntityCount());
			chunks.labelValues(world.getName()).set(world.getChunkCount());
		}
	}

	void close() {
		sampler.cancel();
		server.close();
	}
}
