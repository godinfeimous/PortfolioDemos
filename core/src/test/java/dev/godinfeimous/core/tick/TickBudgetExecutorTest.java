package dev.godinfeimous.core.tick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.godinfeimous.core.tick.TickBudgetExecutor.TickReport;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TickBudgetExecutorTest {

	private static final long MS = 1_000_000;

	/** Reloj falso: el tiempo solo avanza cuando una tarea "trabaja". */
	private static final class FakeClock {
		long now;

		void work(long nanos) {
			now += nanos;
		}
	}

	@Test
	void conCosteEstableNingunTickSePasaDelPresupuesto() {
		FakeClock clock = new FakeClock();
		TickBudgetExecutor executor = new TickBudgetExecutor(() -> clock.now);
		for (int i = 0; i < 1_000; i++) executor.submit("paste", () -> clock.work(300_000)); // 0,3 ms cada bloque de schematic

		List<TickReport> ticks = new ArrayList<>();
		while (executor.queued() > 0) ticks.add(executor.runTick(2 * MS));

		for (TickReport tick : ticks.subList(1, ticks.size())) assertTrue(tick.spentNanos() <= 2 * MS, tick.toString());
		assertEquals(6, ticks.get(2).ran()); // 6 × 0,3 ms = 1,8 ms: la séptima ya no cabe
		assertEquals(1_000, ticks.stream().mapToInt(TickReport::ran).sum());
	}

	@Test
	void conCosteVariableElExcesoQuedaAcotado() {
		FakeClock clock = new FakeClock();
		Random random = new Random(3);
		TickBudgetExecutor executor = new TickBudgetExecutor(() -> clock.now);
		for (int i = 0; i < 5_000; i++) {
			long cost = 100_000 + random.nextLong(400_000); // entre 0,1 y 0,5 ms
			executor.submit("light", () -> clock.work(cost));
		}
		long worst = 0;
		while (executor.queued() > 0) worst = Math.max(worst, executor.runTick(2 * MS).spentNanos());
		// El peor tick se pasa como mucho lo que cuesta una tarea más de lo estimado, nunca el doble del presupuesto.
		assertTrue(worst <= 2 * MS + 500_000, "peor tick: " + worst);
	}

	@Test
	void unaTareaMasCaraQueElPresupuestoNoAtascaLaCola() {
		FakeClock clock = new FakeClock();
		TickBudgetExecutor executor = new TickBudgetExecutor(() -> clock.now);
		executor.submit("huge", () -> clock.work(8 * MS));
		executor.submit("huge", () -> clock.work(8 * MS));
		assertEquals(1, executor.runTick(2 * MS).ran());
		assertEquals(1, executor.runTick(2 * MS).ran());
		assertEquals(0, executor.queued());
	}
}
