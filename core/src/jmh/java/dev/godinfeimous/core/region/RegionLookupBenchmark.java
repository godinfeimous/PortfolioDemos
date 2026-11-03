package dev.godinfeimous.core.region;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * ¿Cuánto cuesta preguntar "qué región manda aquí"? Un pico de vena hace 64 consultas, una explosión cientos.
 * Puntos repartidos como juegan los jugadores: la mitad cerca del centro (spawn y ciudad), la mitad por el mapa.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class RegionLookupBenchmark {

	private static final int POINTS = 1 << 14;

	@Param({"100", "1000", "10000"})
	public int claims;

	private RegionIndex linear;
	private RegionIndex chunked;
	private final int[] xs = new int[POINTS];
	private final int[] zs = new int[POINTS];
	private int next;

	@Setup
	public void setup() {
		Random random = new Random(42);
		List<Region> regions = RegionFixtures.survival(random, claims);
		linear = new LinearRegionIndex(regions);
		chunked = new ChunkRegionIndex(regions);
		for (int i = 0; i < POINTS; i++) {
			int spread = i % 2 == 0 ? 2_000 : RegionFixtures.WORLD_RADIUS;
			xs[i] = random.nextInt(2 * spread) - spread;
			zs[i] = random.nextInt(2 * spread) - spread;
		}
	}

	@Benchmark
	public Region linear() {
		int i = next++ & (POINTS - 1);
		return linear.at(xs[i], zs[i]);
	}

	@Benchmark
	public Region chunked() {
		int i = next++ & (POINTS - 1);
		return chunked.at(xs[i], zs[i]);
	}
}
