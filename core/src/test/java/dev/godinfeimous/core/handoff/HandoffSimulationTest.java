package dev.godinfeimous.core.handoff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.godinfeimous.core.handoff.HandoffSimulation.Config;
import dev.godinfeimous.core.handoff.HandoffSimulation.Result;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class HandoffSimulationTest {

	private static final int SEEDS = 2_000;

	@Test
	void laMismaSemillaDaLoMismo() {
		assertEquals(HandoffSimulation.run(7, Config.network(false)), HandoffSimulation.run(7, Config.network(false)));
	}

	@Test
	void conFencingNingunaEscrituraViejaLlegaALaBaseDeDatos() {
		for (long seed = 0; seed < SEEDS; seed++) {
			Result result = HandoffSimulation.run(seed, Config.network(true));
			assertEquals(0, result.staleWrites(), "semilla " + seed);
			assertEquals(0, result.rollbacks(), "semilla " + seed);
		}
	}

	@Test
	void sinFencingLaSimulacionEncuentraElRollback() {
		long failing = LongStream.range(0, SEEDS).filter(seed -> HandoffSimulation.run(seed, Config.network(false)).rollbacks() > 0).count();
		assertTrue(failing > 0, "la variante sin token debería fallar en alguna semilla");
	}

	@Test
	void sinPausasLasDosVariantesSonIguales() {
		Config calm = new Config(3, 30 * 60_000L, 15_000, 5_000, 10_000, 0, 5_000, 60_000, 30_000, false);
		for (long seed = 0; seed < 200; seed++) {
			Result result = HandoffSimulation.run(seed, calm);
			assertEquals(0, result.staleWrites(), "semilla " + seed);
			assertEquals(0, result.rejected(), "semilla " + seed);
		}
	}
}
