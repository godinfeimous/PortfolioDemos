package dev.godinfeimous.core.handoff;

import java.util.Optional;

/**
 * Modelo del candado por jugador que vive en Redis (SET NX PX + INCR), con el mismo contrato que los scripts Lua
 * del plugin. Cada vez que alguien lo toma, el token sube: es el fencing token con el que la base de datos rechaza
 * las escrituras de un dueño anterior que todavía cree que lo tiene.
 */
public final class LeaseService {

	private final long ttl;
	private Lease current;
	private long counter;

	public LeaseService(long ttl) {
		this.ttl = ttl;
	}

	/** Lo toma si está libre o caducado. Un candado vigente de otro servidor no se pisa nunca. */
	public Optional<Lease> tryAcquire(String owner, long now) {
		if (current != null && current.expiresAt() > now) return Optional.empty();
		current = new Lease(owner, ++counter, now + ttl);
		return Optional.of(current);
	}

	/** Alarga el candado solo si sigue siendo exactamente este (mismo token) y no caducó mientras tanto. */
	public boolean renew(Lease lease, long now) {
		if (!holds(lease, now)) return false;
		current = new Lease(lease.owner(), lease.token(), now + ttl);
		return true;
	}

	public boolean holds(Lease lease, long now) {
		return current != null && current.token() == lease.token() && current.expiresAt() > now;
	}

	public void release(Lease lease) {
		if (current != null && current.token() == lease.token()) current = null;
	}

	/** El token más alto repartido hasta ahora: el del dueño legítimo. */
	public long latestToken() {
		return counter;
	}
}
