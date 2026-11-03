package dev.godinfeimous.core.region;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Un mundo de survival para tests y benchmarks: protecciones de jugador con los radios reales de MICO SERVER
 * (15, 30 y 50 bloques) repartidas por un mapa de 20.000 × 20.000, más un spawn y una ciudad grandes.
 */
public final class RegionFixtures {

	public static final int WORLD_RADIUS = 10_000;
	private static final int[] CLAIM_RADII = {15, 15, 15, 30, 30, 50};

	private RegionFixtures() {}

	public static List<Region> survival(Random random, int claims) {
		List<Region> regions = new ArrayList<>(claims + 2);
		regions.add(new Region("spawn", -250, -250, 250, 250, 100));
		regions.add(new Region("city", -1_500, -1_500, 1_500, 1_500, 10));
		for (int i = 0; i < claims; i++) {
			int r = CLAIM_RADII[random.nextInt(CLAIM_RADII.length)];
			int x = random.nextInt(2 * WORLD_RADIUS) - WORLD_RADIUS;
			int z = random.nextInt(2 * WORLD_RADIUS) - WORLD_RADIUS;
			regions.add(new Region("claim-" + i, x - r, z - r, x + r, z + r, 20));
		}
		return regions;
	}
}
