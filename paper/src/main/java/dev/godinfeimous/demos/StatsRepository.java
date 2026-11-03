package dev.godinfeimous.demos;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Estadísticas en MariaDB/MySQL sin tocar el hilo del servidor.
 * - HikariCP reutiliza conexiones; cada consulta va en un hilo virtual (Java 21+), barato aunque espere a la red.
 * - Las sumas son UPSERT atómicos: dos servidores de la red pueden sumar a la vez sin pisarse.
 * - El UUID va en BINARY(16): la mitad que como texto y el índice es más rápido.
 */
final class StatsRepository implements AutoCloseable {

	@FunctionalInterface
	private interface Binder { void bind(PreparedStatement statement) throws SQLException; }

	private final HikariDataSource pool;
	private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();

	StatsRepository(String url, String user, String password) {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(url);
		config.setUsername(user);
		config.setPassword(password);
		config.setPoolName("demo-stats");
		config.setMaximumPoolSize(6);
		pool = new HikariDataSource(config);
		update("""
				CREATE TABLE IF NOT EXISTS demo_stats (
				  uuid BINARY(16) PRIMARY KEY,
				  ores INT NOT NULL DEFAULT 0
				)""", statement -> { });
	}

	/** El mismo pool lo usa PlayerDataHandoff: una red de conexiones por servidor, no una por sistema. */
	javax.sql.DataSource dataSource() {
		return pool;
	}

	CompletableFuture<Void> addOres(UUID player, int amount) {
		return update("INSERT INTO demo_stats (uuid, ores) VALUES (?, ?) ON DUPLICATE KEY UPDATE ores = ores + VALUES(ores)", statement -> {
			statement.setBytes(1, bytes(player));
			statement.setInt(2, amount);
		});
	}

	CompletableFuture<Integer> ores(UUID player) {
		return CompletableFuture.supplyAsync(() -> {
			try (Connection connection = pool.getConnection();
					PreparedStatement statement = connection.prepareStatement("SELECT ores FROM demo_stats WHERE uuid = ?")) {
				statement.setBytes(1, bytes(player));
				try (ResultSet result = statement.executeQuery()) {
					return result.next() ? result.getInt(1) : 0;
				}
			} catch (SQLException e) {
				throw new CompletionException(e);
			}
		}, io);
	}

	private CompletableFuture<Void> update(String sql, Binder binder) {
		return CompletableFuture.runAsync(() -> {
			try (Connection connection = pool.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
				binder.bind(statement);
				statement.executeUpdate();
			} catch (SQLException e) {
				throw new CompletionException(e);
			}
		}, io);
	}

	private static byte[] bytes(UUID uuid) {
		return ByteBuffer.allocate(16).putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits()).array();
	}

	@Override
	public void close() {
		io.shutdown();
		pool.close();
	}
}
