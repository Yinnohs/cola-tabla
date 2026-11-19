package com.kanbanlive.membership.application.port.in;

import java.util.List;
import java.util.UUID;

/** Miembros de un workspace. Quien llama debe haber comprobado antes canAdmin (@perms). */
public interface ListMembersUseCase {

    List<MembershipView> execute(UUID workspaceId);
}
