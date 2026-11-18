package com.kanbanlive.membership.application.port.in;

import java.util.UUID;

/** Invita a un usuario a un workspace. Quien llama debe haber comprobado antes canAdmin (@perms). */
public interface InviteMemberUseCase {

    MembershipView execute(InviteMemberCommand command);

    /** role: OWNER, EDITOR o VIEWER. */
    record InviteMemberCommand(UUID workspaceId, String userId, String role) {
    }
}
