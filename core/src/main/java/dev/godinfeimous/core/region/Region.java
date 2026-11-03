package dev.godinfeimous.core.region;

import java.util.Comparator;

/** Región rectangular en X/Z, como una protección de WorldGuard vista desde arriba. Los bordes están incluidos. */
public record Region(String id, int minX, int minZ, int maxX, int maxZ, int priority) {

	/** Gana la de más prioridad; a igual prioridad, la de id menor (así los dos índices dan siempre la misma). */
	public static final Comparator<Region> WINNER_FIRST = Comparator.comparingInt(Region::priority).reversed().thenComparing(Region::id);

	public boolean contains(int x, int z) {
		return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
	}

	/** Cuántos chunks de 16×16 toca. */
	public long chunkArea() {
		return (long) ((maxX >> 4) - (minX >> 4) + 1) * ((maxZ >> 4) - (minZ >> 4) + 1);
	}
}
