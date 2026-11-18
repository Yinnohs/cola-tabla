package com.kanbanlive.membership.application.port.in;

import java.util.UUID;

/** Cambia el rol de un miembro. Falla con LAST_OWNER si dejaria el workspace sin OWNER. */
public interface ChangeRoleUseCase {

    MembershipView execute(ChangeRoleCommand command);

    record ChangeRoleCommand(UUID workspaceId, String userId, String role) {
    }
}
