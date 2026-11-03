package dev.godinfeimous.core.region;

/** Responde a "¿qué región manda en este bloque?". Se consulta en cada bloque roto, puesto o pisado. */
public interface RegionIndex {

	/** La región ganadora que contiene el punto, o null si no hay ninguna. */
	Region at(int x, int z);
}
