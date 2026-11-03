package dev.godinfeimous.core.handoff;

import java.util.Locale;

/** Corre la simulación con y sin fencing (10.000 semillas por defecto) y saca la tabla que se ve en el portfolio. */
public final class HandoffReport {

	private HandoffReport() {}

	public static void main(String[] args) {
		int runs = args.length > 0 ? Integer.parseInt(args[0]) : 10_000;
		for (boolean fencing : new boolean[] {false, true}) {
			long start = System.nanoTime();
			int failedRuns = 0;
			long sessions = 0, writes = 0, rejected = 0, stale = 0, rollbacks = 0;
			for (long seed = 0; seed < runs; seed++) {
				HandoffSimulation.Result r = HandoffSimulation.run(seed, HandoffSimulation.Config.network(fencing));
				sessions += r.sessions();
				writes += r.writes();
				rejected += r.rejected();
				stale += r.staleWrites();
				rollbacks += r.rollbacks();
				if (r.staleWrites() > 0 || r.rollbacks() > 0) failedRuns++;
			}
			System.out.printf(Locale.ROOT, "%-10s runs=%d failedRuns=%d sessions=%d writes=%d rejected=%d staleWrites=%d rollbacks=%d ms=%d%n",
					fencing ? "fencing" : "sin-token", runs, failedRuns, sessions, writes, rejected, stale, rollbacks,
					(System.nanoTime() - start) / 1_000_000);
		}
	}
}
