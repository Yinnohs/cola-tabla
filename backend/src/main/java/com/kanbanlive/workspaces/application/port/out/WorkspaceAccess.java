package com.kanbanlive.workspaces.application.port.out;

import java.util.Map;
import java.util.UUID;

/**
 * Workspaces visibles para un usuario y su rol en cada uno (OWNER, EDITOR o VIEWER); lo implementa un adapter hacia
 * membership.port.in.
 */
public interface WorkspaceAccess {

    Map<UUID, String> rolesOf(String userId);
}
