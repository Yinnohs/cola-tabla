package com.kanbanlive.sharedkernel.position;

import java.util.UUID;

/** Nueva posicion para la fila {@code id}; los puertos de persistencia la usan para rebalancear (ADR-0003). */
public record PositionAssignment(UUID id, FractionalIndex position) {
}
