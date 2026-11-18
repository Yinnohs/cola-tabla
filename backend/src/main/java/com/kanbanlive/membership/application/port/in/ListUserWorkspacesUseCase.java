package com.kanbanlive.membership.application.port.in;

import java.util.Set;
import java.util.UUID;

/** Ids de los workspaces de los que el usuario es miembro (para filtrar listados en otras capacidades). */
public interface ListUserWorkspacesUseCase {

    Set<UUID> execute(String userId);
}
