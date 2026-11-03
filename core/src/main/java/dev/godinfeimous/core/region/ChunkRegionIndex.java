package dev.godinfeimous.core.region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Índice por chunk, la misma idea que el ChunkHashTable de WorldGuard: cada chunk guarda las regiones que lo tocan,
 * ya ordenadas de ganadora a perdedora, así una consulta mira un solo chunk y para en la primera que contiene el punto.
 * - Las claves son long (cx << 32 | cz) en una tabla de direccionamiento abierto: sin boxing ni basura por consulta.
 * - Las regiones enormes (spawn, ciudad, __global__) irían a miles de chunks: van aparte, en una lista corta.
 * El coste no depende de cuántas regiones haya en el mundo, solo de cuántas se solapan en ese chunk.
 */
public final class ChunkRegionIndex implements RegionIndex {

	/** A partir de aquí una región se considera grande y no se reparte por chunks. */
	static final long LARGE_CHUNKS = 1_024;

	private final long[] keys;
	private final Region[][] buckets;
	private final int mask;
	private final Region[] large;

	public ChunkRegionIndex(List<Region> regions) {
		Map<Long, List<Region>> byChunk = new HashMap<>();
		List<Region> big = new ArrayList<>();
		for (Region region : regions) {
			if (region.chunkArea() > LARGE_CHUNKS) {
				big.add(region);
				continue;
			}
			for (int cx = region.minX() >> 4; cx <= region.maxX() >> 4; cx++) {
				for (int cz = region.minZ() >> 4; cz <= region.maxZ() >> 4; cz++) {
					byChunk.computeIfAbsent(key(cx, cz), k -> new ArrayList<>()).add(region);
				}
			}
		}
		int capacity = Integer.highestOneBit(Math.max(16, byChunk.size() * 2 - 1)) << 1;
		keys = new long[capacity];
		buckets = new Region[capacity][];
		mask = capacity - 1;
		for (Map.Entry<Long, List<Region>> entry : byChunk.entrySet()) {
			List<Region> list = entry.getValue();
			list.sort(Region.WINNER_FIRST);
			int slot = slot(entry.getKey());
			while (buckets[slot] != null) slot = (slot + 1) & mask;
			keys[slot] = entry.getKey();
			buckets[slot] = list.toArray(Region[]::new);
		}
		big.sort(Region.WINNER_FIRST);
		large = big.toArray(Region[]::new);
	}

	@Override
	public Region at(int x, int z) {
		Region local = null;
		long key = key(x >> 4, z >> 4);
		for (int slot = slot(key); buckets[slot] != null; slot = (slot + 1) & mask) {
			if (keys[slot] != key) continue;
			for (Region region : buckets[slot]) {
				if (region.contains(x, z)) {
					local = region;
					break;
				}
			}
			break;
		}
		for (Region region : large) {
			if (local != null && Region.WINNER_FIRST.compare(local, region) <= 0) break;
			if (region.contains(x, z)) return region;
		}
		return local;
	}

	private static long key(int cx, int cz) {
		return ((long) cx << 32) | (cz & 0xFFFF_FFFFL);
	}

	/** Mezcla de bits de MurmurHash3 (fmix64): chunks vecinos no caen en huecos vecinos. */
	private int slot(long key) {
		long h = key;
		h ^= h >>> 33;
		h *= 0xff51afd7ed558ccdL;
		h ^= h >>> 33;
		h *= 0xc4ceb9fe1a85ec53L;
		h ^= h >>> 33;
		return (int) h & mask;
	}
}
