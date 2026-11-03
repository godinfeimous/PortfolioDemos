package dev.godinfeimous.demos;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Inventario, vida y nivel compartidos por toda la red, sin duplicar ítems al cambiar de servidor.
 *
 * Candado en Redis, datos en MariaDB. El candado solo no basta: un servidor comprueba que lo tiene, manda la escritura
 * y se congela (GC largo, host saturado). El candado caduca, el jugador sigue en otro servidor, y cuando el primero
 * despierta su escritura vieja llega después y lo devuelve atrás: un rollback, la forma clásica de duplicar ítems.
 * Por eso cada vez que un servidor toma el candado recibe un fencing token (INCR), y MariaDB solo acepta escrituras
 * con un token igual o mayor que el que tiene guardado:
 * 1. Prelogin (hilo aparte): SET NX PX + INCR en un mismo script → token. Si en 3 s no se libera, se rechaza la entrada.
 * 2. claim: el token nuevo se escribe en MariaDB antes de leer. Desde ahí el servidor anterior ya no puede escribir.
 * 3. En juego: el candado se renueva cada 5 s; si se pierde, el jugador sale de este servidor.
 * 4. Al salir: UPDATE ... WHERE token <= ? y solo cuando MariaDB confirma se suelta el candado.
 * El protocolo está simulado en dev.godinfeimous.core.handoff (10.000 semillas, 0 rollbacks).
 */
final class PlayerDataHandoff implements Listener {

	private static final long LOCK_TTL_MS = 15_000;
	private static final long WAIT_FOR_LOCK_MS = 3_000;
	private static final byte FORMAT = 1;

	/** Toma el candado y, en la misma operación atómica, saca el siguiente fencing token. 0 si está ocupado. */
	private static final String ACQUIRE = """
			if redis.call('SET', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then
			  return redis.call('INCR', KEYS[2])
			end
			return 0""";
	/** Renueva o suelta el candado solo si sigue siendo nuestro (el valor lleva el servidor y un nonce por sesión). */
	private static final String RENEW = """
			if redis.call('GET', KEYS[1]) == ARGV[1] then
			  return redis.call('PEXPIRE', KEYS[1], ARGV[2])
			end
			return 0""";
	private static final String RELEASE = """
			if redis.call('GET', KEYS[1]) == ARGV[1] then
			  return redis.call('DEL', KEYS[1])
			end
			return 0""";

	private record Lease(String owner, long token) {}

	private record Snapshot(double health, int level, float exp, ItemStack[] inventory) {}

	private final JavaPlugin plugin;
	private final String serverId;
	private final DataSource db;
	private final RedisClient client;
	private final StatefulRedisConnection<String, String> connection;
	private final RedisCommands<String, String> redis;
	private final ExecutorService io = Executors.newVirtualThreadPerTaskExecutor();
	private final Map<UUID, Lease> leases = new ConcurrentHashMap<>();
	/** Snapshots cargados en el prelogin, a la espera de que el jugador exista en el mundo. */
	private final Map<UUID, Snapshot> pending = new ConcurrentHashMap<>();

	PlayerDataHandoff(JavaPlugin plugin, String redisUri, String serverId, DataSource db) {
		this.plugin = plugin;
		this.serverId = serverId;
		this.db = db;
		this.client = RedisClient.create(redisUri);
		this.connection = client.connect();
		this.redis = connection.sync();
		sql("CREATE TABLE IF NOT EXISTS player_data (uuid BINARY(16) PRIMARY KEY, token BIGINT NOT NULL, data MEDIUMBLOB NULL)", s -> { });
		plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, task -> renewAll(), 5, 5, TimeUnit.SECONDS);
	}

	private static String lockKey(UUID id) { return "sync:lock:" + id; }
	private static String tokenKey(UUID id) { return "sync:token:" + id; }

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPreLogin(AsyncPlayerPreLoginEvent event) {
		if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
		UUID id = event.getUniqueId();
		try {
			Lease lease = acquire(id);
			if (lease == null) {
				event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
						Component.text("Tus datos se están guardando en otro servidor. Vuelve a entrar en un segundo.", NamedTextColor.YELLOW));
				return;
			}
			byte[] data = claimAndRead(id, lease.token());
			leases.put(id, lease);
			if (data != null) pending.put(id, decode(data));
		} catch (Exception e) {
			plugin.getSLF4JLogger().error("No se pudieron cargar los datos de {}", id, e);
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("No se pudieron cargar tus datos.", NamedTextColor.RED));
		}
	}

	/** Espera (con reintentos cortos) a que el servidor anterior suelte el candado, y lo toma con su token. */
	private Lease acquire(UUID id) throws InterruptedException {
		String owner = serverId + ":" + UUID.randomUUID();
		long deadline = System.currentTimeMillis() + WAIT_FOR_LOCK_MS;
		while (System.currentTimeMillis() < deadline) {
			Long token = redis.eval(ACQUIRE, ScriptOutputType.INTEGER, new String[] {lockKey(id), tokenKey(id)}, owner, Long.toString(LOCK_TTL_MS));
			if (token != null && token > 0) return new Lease(owner, token);
			Thread.sleep(100); // hilo de prelogin: esperar aquí no frena el servidor
		}
		return null;
	}

	/** claim antes de leer: si se leyera primero, una escritura vieja en vuelo podría colarse entre medias. */
	private byte[] claimAndRead(UUID id, long token) throws SQLException {
		try (Connection c = db.getConnection()) {
			try (PreparedStatement insert = c.prepareStatement("INSERT IGNORE INTO player_data (uuid, token, data) VALUES (?, 0, NULL)")) {
				insert.setBytes(1, bytes(id));
				insert.executeUpdate();
			}
			try (PreparedStatement claim = c.prepareStatement("UPDATE player_data SET token = ? WHERE uuid = ? AND token < ?")) {
				claim.setLong(1, token);
				claim.setBytes(2, bytes(id));
				claim.setLong(3, token);
				// Si no sube, MariaDB ya vio un token mayor: Redis perdió su contador (¿reinicio sin appendonly?).
				if (claim.executeUpdate() == 0) throw new SQLException("Token " + token + " por detrás del guardado para " + id);
			}
			try (PreparedStatement read = c.prepareStatement("SELECT data FROM player_data WHERE uuid = ?")) {
				read.setBytes(1, bytes(id));
				try (ResultSet result = read.executeQuery()) {
					return result.next() ? result.getBytes(1) : null;
				}
			}
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		Snapshot snapshot = pending.remove(player.getUniqueId());
		if (snapshot == null) return;
		player.getInventory().setContents(snapshot.inventory());
		AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
		player.setHealth(Math.min(snapshot.health(), maxHealth == null ? 20 : maxHealth.getValue()));
		player.setLevel(snapshot.level());
		player.setExp(snapshot.exp());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		Lease lease = leases.remove(id);
		if (lease == null) return;
		save(event.getPlayer(), lease).thenRun(() -> redis.eval(RELEASE, ScriptOutputType.INTEGER, new String[] {lockKey(id)}, lease.owner()));
	}

	/** El inventario se lee en el hilo del jugador; la escritura con fencing va en un hilo virtual. */
	private CompletableFuture<Boolean> save(Player player, Lease lease) {
		UUID id = player.getUniqueId();
		byte[] data = encode(new Snapshot(player.getHealth(), player.getLevel(), player.getExp(), player.getInventory().getContents()));
		return CompletableFuture.supplyAsync(() -> {
			int rows = sql("UPDATE player_data SET data = ?, token = ? WHERE uuid = ? AND token <= ?", s -> {
				s.setBytes(1, data);
				s.setLong(2, lease.token());
				s.setBytes(3, bytes(id));
				s.setLong(4, lease.token());
			});
			if (rows == 0) plugin.getSLF4JLogger().warn("Escritura de {} rechazada: otro servidor ya tiene al jugador (token {})", id, lease.token());
			return rows == 1;
		}, io);
	}

	/**
	 * Cada 5 s: alarga los candados de quien sigue aquí. El que no se puede renovar ya es de otro servidor.
	 * Si Redis no responde no se echa a nadie: sin Redis nadie puede emitir un token mayor, así que las escrituras
	 * de los que ya juegan siguen siendo válidas en MariaDB. Solo las entradas nuevas se rechazan.
	 */
	private void renewAll() {
		for (Map.Entry<UUID, Lease> entry : leases.entrySet()) {
			Long ok;
			try {
				ok = redis.eval(RENEW, ScriptOutputType.INTEGER, new String[] {lockKey(entry.getKey())}, entry.getValue().owner(), Long.toString(LOCK_TTL_MS));
			} catch (RedisException e) {
				plugin.getSLF4JLogger().warn("Redis no responde: candados sin renovar, las entradas nuevas se rechazan", e);
				return;
			}
			if (ok != null && ok == 1) continue;
			leases.remove(entry.getKey());
			Player player = plugin.getServer().getPlayer(entry.getKey());
			if (player != null) player.getScheduler().run(plugin, task -> player.kick(Component.text("Tu sesión pasó a otro servidor.")), null);
		}
	}

	void close() {
		// Al apagar: guardar a todos y soltar sus candados, con tope para no colgar el apagado.
		CompletableFuture.allOf(plugin.getServer().getOnlinePlayers().stream()
						.filter(player -> leases.containsKey(player.getUniqueId()))
						.map(player -> save(player, leases.get(player.getUniqueId())))
						.toArray(CompletableFuture[]::new))
				.orTimeout(5, TimeUnit.SECONDS)
				.exceptionally(error -> null)
				.join();
		leases.forEach((id, lease) -> redis.eval(RELEASE, ScriptOutputType.INTEGER, new String[] {lockKey(id)}, lease.owner()));
		io.close();
		connection.close();
		client.shutdown();
	}

	@FunctionalInterface
	private interface Binder { void bind(PreparedStatement statement) throws SQLException; }

	private int sql(String query, Binder binder) {
		try (Connection c = db.getConnection(); PreparedStatement statement = c.prepareStatement(query)) {
			binder.bind(statement);
			return statement.executeUpdate();
		} catch (SQLException e) {
			throw new IllegalStateException(e);
		}
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
	}

	private static byte[] encode(Snapshot snapshot) {
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeByte(FORMAT);
			out.writeDouble(snapshot.health());
			out.writeInt(snapshot.level());
			out.writeFloat(snapshot.exp());
			byte[] items = ItemStack.serializeItemsAsBytes(snapshot.inventory());
			out.writeInt(items.length);
			out.write(items);
			out.flush();
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Snapshot decode(byte[] data) throws IOException {
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			if (in.readByte() != FORMAT) throw new IOException("Formato de snapshot desconocido");
			double health = in.readDouble();
			int level = in.readInt();
			float exp = in.readFloat();
			byte[] items = in.readNBytes(in.readInt());
			return new Snapshot(health, level, exp, ItemStack.deserializeItemsFromBytes(items));
		}
	}
}
