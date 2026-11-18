package com.kanbanlive.workspaces.adapter.out.membership;

import com.kanbanlive.membership.application.port.in.ListUserWorkspacesUseCase;
import com.kanbanlive.workspaces.application.port.out.WorkspaceAccess;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Cruce entre capacidades permitido: workspaces -> membership SOLO via su port.in. */
@Component
class WorkspaceAccessAdapter implements WorkspaceAccess {

    private final ListUserWorkspacesUseCase listUserWorkspaces;

    WorkspaceAccessAdapter(ListUserWorkspacesUseCase listUserWorkspaces) {
        this.listUserWorkspaces = listUserWorkspaces;
    }

    @Override
    public Set<UUID> workspaceIdsOf(String userId) {
        return listUserWorkspaces.execute(userId);
    }
}
