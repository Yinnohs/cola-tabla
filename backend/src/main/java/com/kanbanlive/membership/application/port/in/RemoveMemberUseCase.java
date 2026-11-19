package com.kanbanlive.membership.application.port.in;

import java.util.UUID;

/**
 * Quita a un miembro del workspace. NotFound si no es miembro; falla con LAST_OWNER si era el ultimo OWNER. Quien
 * llama debe haber comprobado antes canAdmin (@perms).
 */
public interface RemoveMemberUseCase {

    void execute(UUID workspaceId, String userId);
}
