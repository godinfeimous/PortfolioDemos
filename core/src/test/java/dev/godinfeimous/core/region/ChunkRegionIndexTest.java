package dev.godinfeimous.core.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ChunkRegionIndexTest {

	/** El índice rápido tiene que dar exactamente lo mismo que recorrer todas las regiones, en cualquier punto. */
	@Test
	void coincideConElRecorridoCompleto() {
		Random random = new Random(1);
		List<Region> regions = RegionFixtures.survival(random, 5_000);
		RegionIndex linear = new LinearRegionIndex(regions);
		RegionIndex chunked = new ChunkRegionIndex(regions);
		for (int i = 0; i < 200_000; i++) {
			// La mitad de los puntos cerca del centro, donde se solapan spawn, ciudad y protecciones.
			int spread = i % 2 == 0 ? 2_000 : RegionFixtures.WORLD_RADIUS;
			int x = random.nextInt(2 * spread) - spread, z = random.nextInt(2 * spread) - spread;
			assertEquals(linear.at(x, z), chunked.at(x, z), "punto " + x + ", " + z);
		}
	}

	@Test
	void losBordesCuentanYLosNegativosTambien() {
		Region claim = new Region("a", -17, -17, -1, -1, 20);
		RegionIndex index = new ChunkRegionIndex(List.of(claim));
		assertEquals(claim, index.at(-17, -1));
		assertEquals(claim, index.at(-1, -17));
		assertNull(index.at(0, 0));
		assertNull(index.at(-18, -5));
	}

	@Test
	void laDeMasPrioridadGanaAunqueSeaGrande() {
		Region spawn = new Region("spawn", -5_000, -5_000, 5_000, 5_000, 100);
		Region claim = new Region("claim", 0, 0, 30, 30, 20);
		assertEquals(spawn, new ChunkRegionIndex(List.of(spawn, claim)).at(10, 10));
		assertEquals(claim, new ChunkRegionIndex(List.of(new Region("city", -5_000, -5_000, 5_000, 5_000, 10), claim)).at(10, 10));
	}
}
