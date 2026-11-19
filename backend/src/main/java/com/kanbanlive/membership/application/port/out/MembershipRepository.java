package com.kanbanlive.membership.application.port.out;

import com.kanbanlive.membership.domain.model.Membership;
import com.kanbanlive.membership.domain.model.Role;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface MembershipRepository {

    Membership save(Membership membership);

    Optional<Membership> findByWorkspaceAndUser(UUID workspaceId, String userId);

    Set<UUID> findWorkspaceIdsByUser(String userId);

    /** Membresias del workspace, ordenadas por fecha de alta y luego userId (estable). */
    List<Membership> findByWorkspace(UUID workspaceId);

    List<Membership> findByUser(String userId);

    void delete(Membership membership);

    long countByWorkspaceAndRole(UUID workspaceId, Role role);
}
