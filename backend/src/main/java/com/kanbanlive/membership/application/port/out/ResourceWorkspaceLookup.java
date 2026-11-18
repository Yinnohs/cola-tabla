package com.kanbanlive.membership.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Resuelve el workspace de un recurso (tablero, lista o tarjeta) via los port.in de boards/lists/cards. */
public interface ResourceWorkspaceLookup {

    Optional<UUID> workspaceOf(UUID resourceId);
}
