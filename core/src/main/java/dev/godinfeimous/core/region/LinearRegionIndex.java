package dev.godinfeimous.core.region;

import java.util.List;

/** Lo que hace un plugin de protecciones hecho a mano: recorrer todas las regiones en cada consulta. */
public final class LinearRegionIndex implements RegionIndex {

	private final Region[] regions;

	public LinearRegionIndex(List<Region> regions) {
		this.regions = regions.toArray(Region[]::new);
	}

	@Override
	public Region at(int x, int z) {
		Region best = null;
		for (Region region : regions) {
			if (region.contains(x, z) && (best == null || Region.WINNER_FIRST.compare(region, best) < 0)) best = region;
		}
		return best;
	}
}
