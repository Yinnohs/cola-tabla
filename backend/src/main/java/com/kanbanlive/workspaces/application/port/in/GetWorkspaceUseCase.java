package com.kanbanlive.workspaces.application.port.in;

import java.util.UUID;

public interface GetWorkspaceUseCase {

    /** Lanza NotFoundException (sharedkernel) si el workspace no existe. */
    WorkspaceView execute(UUID workspaceId);
}
