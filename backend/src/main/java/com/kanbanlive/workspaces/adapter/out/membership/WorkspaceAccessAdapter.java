package com.kanbanlive.workspaces.adapter.out.membership;

import com.kanbanlive.membership.application.port.in.GetUserRolesUseCase;
import com.kanbanlive.workspaces.application.port.out.WorkspaceAccess;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Cruce entre capacidades permitido: workspaces -> membership SOLO via su port.in. */
@Component
class WorkspaceAccessAdapter implements WorkspaceAccess {

    private final GetUserRolesUseCase getUserRoles;

    WorkspaceAccessAdapter(GetUserRolesUseCase getUserRoles) {
        this.getUserRoles = getUserRoles;
    }

    @Override
    public Map<UUID, String> rolesOf(String userId) {
        return getUserRoles.execute(userId);
    }
}
