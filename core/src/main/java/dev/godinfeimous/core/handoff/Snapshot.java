package dev.godinfeimous.core.handoff;

/** Los datos del jugador tal como se guardan: su progreso y el token del candado con el que se escribieron. */
public record Snapshot(long progress, long token) {}
