package com.kanbanlive.membership.adapter.out.persistence;

import com.kanbanlive.membership.domain.model.Role;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MembershipJpaRepository extends JpaRepository<MembershipJpaEntity, UUID> {

    Optional<MembershipJpaEntity> findByWorkspaceIdAndUserId(UUID workspaceId, String userId);

    @Query("select m.workspaceId from MembershipJpaEntity m where m.userId = :userId")
    Set<UUID> findWorkspaceIdsByUserId(@Param("userId") String userId);

    List<MembershipJpaEntity> findByWorkspaceIdOrderByCreatedAtAscUserIdAsc(UUID workspaceId);

    List<MembershipJpaEntity> findByUserId(String userId);

    long countByWorkspaceIdAndRole(UUID workspaceId, Role role);
}
