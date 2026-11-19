package com.kanbanlive.membership.application.port.in;

import java.util.Map;
import java.util.UUID;

/** Rol (OWNER, EDITOR o VIEWER) del usuario en cada workspace del que es miembro; sus claves son los workspaces visibles. */
public interface GetUserRolesUseCase {

    Map<UUID, String> execute(String userId);
}
