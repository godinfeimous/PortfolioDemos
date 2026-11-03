package dev.godinfeimous.core.handoff;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Simulación determinista del cambio de servidor, en la línea de las pruebas de FoundationDB o Jepsen: un jugador
 * salta entre servidores durante 30 minutos simulados mientras algunas escrituras se quedan colgadas (GC largo, host
 * saturado, red que se corta). Con la misma semilla sale siempre lo mismo, así que un fallo se repite paso a paso.
 *
 * El caso peligroso: el servidor A comprueba el candado en Redis, manda la escritura y se congela. El candado caduca,
 * el proxy lleva al jugador a B, B juega y guarda. Cuando A despierta, su escritura vieja llega a la base de datos.
 * Sin fencing token la acepta y el jugador vuelve atrás: un rollback, que es la forma clásica de duplicar ítems.
 *
 * Invariantes: ninguna escritura aceptada viene de un dueño anterior (staleWrites) y el progreso guardado nunca
 * retrocede (rollbacks).
 */
public final class HandoffSimulation {

	public record Config(int servers, long durationMs, long leaseTtlMs, long renewEveryMs, long autosaveEveryMs,
			double stallChance, long minStallMs, long maxStallMs, long clientTimeoutMs, boolean fencing) {

		/**
		 * Una red normal: 3 servidores, candado de 15 s renovado cada 5 s, autosave cada 10 s, el cliente se
		 * desconecta tras 30 s sin respuesta, y el 2 % de las escrituras se cuelga entre 5 y 60 s.
		 */
		public static Config network(boolean fencing) {
			return new Config(3, 30 * 60_000L, 15_000, 5_000, 10_000, 0.02, 5_000, 60_000, 30_000, fencing);
		}
	}

	/** Lo que pasó en una ejecución. Con fencing, staleWrites y rollbacks tienen que salir siempre a 0. */
	public record Result(int sessions, int writes, int rejected, int staleWrites, int rollbacks) {}

	private record Event(long at, long order, Runnable action) {}

	/** El jugador en un servidor. Deja de valer cuando ese servidor pierde el candado o el jugador sale. */
	private static final class Session {
		final int server;
		final Lease lease;
		long progress;
		boolean open = true;

		Session(int server, Lease lease, long progress) {
			this.server = server;
			this.lease = lease;
			this.progress = progress;
		}
	}

	private final Config config;
	private final Random random;
	private final PriorityQueue<Event> events = new PriorityQueue<>(Comparator.comparingLong(Event::at).thenComparingLong(Event::order));
	private final LeaseService leases;
	private final SnapshotStore store;
	private final long[] pausedUntil;
	private final long[] lastDelivery;
	/** Dónde está el jugador ahora; null mientras cambia de servidor. */
	private Session current;
	private long now;
	private long order;
	private long committed;
	private int sessions, writes, rejected, staleWrites, rollbacks;

	private HandoffSimulation(long seed, Config config) {
		this.config = config;
		this.random = new Random(seed);
		this.leases = new LeaseService(config.leaseTtlMs());
		this.store = config.fencing() ? SnapshotStore.fenced() : SnapshotStore.unfenced();
		this.pausedUntil = new long[config.servers()];
		this.lastDelivery = new long[config.servers()];
	}

	public static Result run(long seed, Config config) {
		HandoffSimulation sim = new HandoffSimulation(seed, config);
		sim.at(0, () -> sim.join(sim.random.nextInt(config.servers())));
		while (!sim.events.isEmpty() && sim.events.peek().at() <= config.durationMs()) {
			Event event = sim.events.poll();
			sim.now = event.at();
			event.action().run();
		}
		return new Result(sim.sessions, sim.writes, sim.rejected, sim.staleWrites, sim.rollbacks);
	}

	/** Entrar: tomar el candado, reclamar los datos con el token nuevo y solo entonces leerlos. */
	private void join(int server) {
		onServer(server, () -> {
			var lease = leases.tryAcquire("server-" + server, now);
			if (lease.isEmpty()) { // el anterior todavía no lo suelta: el proxy reintenta en medio segundo
				at(now + 500, () -> join(server));
				return;
			}
			store.claim(lease.get().token());
			Session session = new Session(server, lease.get(), store.read().progress());
			current = session;
			sessions++;
			every(1_000, session, () -> { if (current == session) session.progress++; });
			every(config.renewEveryMs(), session, () -> { if (!leases.renew(session.lease, now)) lose(session); });
			every(config.autosaveEveryMs(), session, () -> save(session, () -> { }));
			at(now + 5_000 + random.nextLong(55_000), () -> onServer(server, () -> quit(session)));
		});
	}

	/** Salir: guardar y soltar el candado cuando la base de datos confirma. Soltarlo antes deja leer datos viejos. */
	private void quit(Session session) {
		if (session.open) {
			save(session, () -> leases.release(session.lease));
			session.open = false;
		}
		if (current == session) hop(session.server);
	}

	/** Comprueba el candado (Redis) y manda la escritura (base de datos). Entre una cosa y la otra puede pasar de todo. */
	private void save(Session session, Runnable confirmed) {
		if (!leases.holds(session.lease, now)) {
			lose(session);
			return;
		}
		Snapshot snapshot = new Snapshot(session.progress, session.lease.token());
		long delivery = now + 2 + random.nextLong(18);
		if (random.nextDouble() < config.stallChance()) {
			long stall = config.minStallMs() + random.nextLong(config.maxStallMs() - config.minStallMs());
			pausedUntil[session.server] = now + stall;
			delivery += stall;
			if (stall > config.clientTimeoutMs()) {
				at(now + config.clientTimeoutMs(), () -> { if (current == session) hop(session.server); });
			}
		}
		// Una sola conexión por servidor: sus escrituras llegan en orden.
		delivery = Math.max(delivery, lastDelivery[session.server] + 1);
		lastDelivery[session.server] = delivery;
		at(delivery, () -> deliver(snapshot, confirmed));
	}

	private void deliver(Snapshot snapshot, Runnable confirmed) {
		boolean stale = snapshot.token() < leases.latestToken();
		if (!store.write(snapshot)) {
			rejected++;
		} else {
			writes++;
			if (stale) staleWrites++;
			if (snapshot.progress() < committed) rollbacks++;
			committed = snapshot.progress();
		}
		confirmed.run();
	}

	/** El servidor perdió el candado: la sesión ya no puede guardar y el jugador sale de ahí. */
	private void lose(Session session) {
		session.open = false;
		if (current == session) hop(session.server);
	}

	private void hop(int from) {
		current = null;
		int next = (from + 1 + random.nextInt(config.servers() - 1)) % config.servers();
		at(now + 1_000 + random.nextLong(2_000), () -> join(next));
	}

	private void every(long period, Session session, Runnable action) {
		at(now + period, () -> onServer(session.server, () -> {
			if (!session.open) return;
			action.run();
			every(period, session, action);
		}));
	}

	/** Un servidor congelado no hace nada: lo suyo espera a que se le pase la pausa. */
	private void onServer(int server, Runnable action) {
		if (pausedUntil[server] > now) at(pausedUntil[server], () -> onServer(server, action));
		else action.run();
	}

	private void at(long time, Runnable action) {
		events.add(new Event(time, order++, action));
	}
}
