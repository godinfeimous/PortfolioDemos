package dev.godinfeimous.core.tick;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Reparte trabajo pesado del hilo del servidor (pegar un schematic, regenerar una arena, migrar datos de chunks)
 * entre ticks sin pasarse de un presupuesto. Antes de cada tarea estima lo que va a costar con la media móvil
 * exponencial de las anteriores de su tipo y, si no cabe en lo que queda, la deja para el tick siguiente.
 * Así el MSPT queda acotado aunque la cola tenga miles de tareas.
 *
 * El reloj se inyecta: en producción System::nanoTime, en los tests uno falso para que el resultado sea exacto.
 */
public final class TickBudgetExecutor {

	/** Lo que hizo un tick: tareas ejecutadas, las que quedan en cola y el tiempo gastado. */
	public record TickReport(int ran, int queued, long spentNanos) {}

	private record Task(String kind, Runnable work) {}

	/** Media móvil exponencial: pesa más lo reciente, así se adapta si una tarea empieza a costar más. */
	private static final class Ewma {
		private static final double ALPHA = 0.25;
		private double value;
		private boolean seeded;

		void add(long sample) {
			value = seeded ? value + ALPHA * (sample - value) : sample;
			seeded = true;
		}

		long estimate() {
			return (long) value;
		}
	}

	private final LongSupplier clock;
	private final ArrayDeque<Task> queue = new ArrayDeque<>();
	private final Map<String, Ewma> costs = new HashMap<>();

	public TickBudgetExecutor(LongSupplier clock) {
		this.clock = clock;
	}

	public void submit(String kind, Runnable work) {
		queue.addLast(new Task(kind, work));
	}

	public int queued() {
		return queue.size();
	}

	/** Llamado una vez por tick. Siempre ejecuta al menos una tarea, para que una tarea cara nunca atasque la cola. */
	public TickReport runTick(long budgetNanos) {
		long start = clock.getAsLong();
		int ran = 0;
		while (!queue.isEmpty()) {
			Task task = queue.peekFirst();
			Ewma cost = costs.computeIfAbsent(task.kind(), kind -> new Ewma());
			long spent = clock.getAsLong() - start;
			if (ran > 0 && spent + cost.estimate() > budgetNanos) break;
			queue.pollFirst();
			long before = clock.getAsLong();
			task.work().run();
			cost.add(clock.getAsLong() - before);
			ran++;
		}
		return new TickReport(ran, queue.size(), clock.getAsLong() - start);
	}
}
