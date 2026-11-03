package dev.godinfeimous.core.handoff;

/** Lo que entrega el candado al servidor que lo toma: el dueño, su fencing token y cuándo caduca. */
public record Lease(String owner, long token, long expiresAt) {}
