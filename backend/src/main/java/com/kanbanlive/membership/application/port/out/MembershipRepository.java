package com.kanbanlive.membership.application.port.out;

import com.kanbanlive.membership.domain.model.Membership;
import com.kanbanlive.membership.domain.model.Role;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface MembershipRepository {

    Membership save(Membership membership);

    Optional<Membership> findByWorkspaceAndUser(UUID workspaceId, String userId);

    Set<UUID> findWorkspaceIdsByUser(String userId);

    long countByWorkspaceAndRole(UUID workspaceId, Role role);
}
