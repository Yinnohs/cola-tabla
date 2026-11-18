package com.kanbanlive.membership.application.port.in;

import java.util.UUID;

/** Lo usa workspaces al crear un workspace: el creador pasa a ser OWNER (idempotente). */
public interface AssignWorkspaceOwnerUseCase {

    void execute(UUID workspaceId, String userId);
}
