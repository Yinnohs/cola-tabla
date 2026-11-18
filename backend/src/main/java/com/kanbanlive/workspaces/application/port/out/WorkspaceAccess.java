package com.kanbanlive.workspaces.application.port.out;

import java.util.Set;
import java.util.UUID;

/** Workspaces visibles para un usuario (lo implementa un adapter hacia membership.port.in). */
public interface WorkspaceAccess {

    Set<UUID> workspaceIdsOf(String userId);
}
