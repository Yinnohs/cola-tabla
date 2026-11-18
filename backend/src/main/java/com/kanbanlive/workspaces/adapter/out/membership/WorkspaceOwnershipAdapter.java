package com.kanbanlive.workspaces.adapter.out.membership;

import com.kanbanlive.membership.application.port.in.AssignWorkspaceOwnerUseCase;
import com.kanbanlive.workspaces.application.port.out.WorkspaceOwnership;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Cruce entre capacidades permitido: workspaces -> membership SOLO via su port.in (misma transaccion). */
@Component
class WorkspaceOwnershipAdapter implements WorkspaceOwnership {

    private final AssignWorkspaceOwnerUseCase assignOwner;

    WorkspaceOwnershipAdapter(AssignWorkspaceOwnerUseCase assignOwner) {
        this.assignOwner = assignOwner;
    }

    @Override
    public void assignOwner(UUID workspaceId, String userId) {
        assignOwner.execute(workspaceId, userId);
    }
}
