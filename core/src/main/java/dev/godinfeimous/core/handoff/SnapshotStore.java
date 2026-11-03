package dev.godinfeimous.core.handoff;

/**
 * Donde viven los datos del jugador (MariaDB en la red real). Las dos variantes de la simulación:
 * - fenced: la base de datos conoce el token. En SQL:
 *     claim:  UPDATE player_data SET token = ? WHERE uuid = ? AND token < ?
 *     write:  UPDATE player_data SET data = ?, token = ? WHERE uuid = ? AND token <= ?
 * - unfenced: acepta todo lo que le llega, aunque el candado se haya comprobado antes en Redis.
 */
public interface SnapshotStore {

	Snapshot read();

	/** Al tomar el candado: sube el token guardado al del nuevo dueño. Desde ahí, el anterior ya no puede escribir. */
	void claim(long token);

	/** false si la escritura se rechazó por venir de un dueño anterior. */
	boolean write(Snapshot snapshot);

	static SnapshotStore fenced() {
		return new InMemory(true);
	}

	static SnapshotStore unfenced() {
		return new InMemory(false);
	}

	final class InMemory implements SnapshotStore {
		private final boolean fencing;
		private Snapshot stored = new Snapshot(0, 0);

		private InMemory(boolean fencing) {
			this.fencing = fencing;
		}

		@Override
		public Snapshot read() {
			return stored;
		}

		@Override
		public void claim(long token) {
			if (fencing && token > stored.token()) stored = new Snapshot(stored.progress(), token);
		}

		@Override
		public boolean write(Snapshot snapshot) {
			if (fencing && snapshot.token() < stored.token()) return false;
			stored = snapshot;
			return true;
		}
	}
}
