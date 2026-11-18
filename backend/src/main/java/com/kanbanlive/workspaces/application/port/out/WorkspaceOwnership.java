package com.kanbanlive.workspaces.application.port.out;

import java.util.UUID;

/** Registra al creador de un workspace como OWNER (lo implementa un adapter hacia membership.port.in). */
public interface WorkspaceOwnership {

    void assignOwner(UUID workspaceId, String userId);
}
