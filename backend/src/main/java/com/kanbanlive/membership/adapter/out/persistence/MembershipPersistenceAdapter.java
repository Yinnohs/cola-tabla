package com.kanbanlive.membership.adapter.out.persistence;

import com.kanbanlive.membership.application.port.out.MembershipRepository;
import com.kanbanlive.membership.domain.model.Membership;
import com.kanbanlive.membership.domain.model.Role;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class MembershipPersistenceAdapter implements MembershipRepository {

    private final MembershipJpaRepository jpa;
    private final MembershipEntityMapper mapper;

    MembershipPersistenceAdapter(MembershipJpaRepository jpa, MembershipEntityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Membership save(Membership membership) {
        try {
            MembershipJpaEntity entity = jpa.findById(membership.id()).map(existing -> {
                if (existing.getVersion() != null && existing.getVersion() != membership.version()) {
                    throw new StaleVersionException("membership " + membership.id() + " was modified concurrently");
                }
                mapper.apply(membership, existing);
                return existing;
            }).orElseGet(() -> mapper.toNewEntity(membership));
            return mapper.toDomain(jpa.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleVersionException("membership " + membership.id() + " was modified concurrently");
        }
    }

    @Override
    public Optional<Membership> findByWorkspaceAndUser(UUID workspaceId, String userId) {
        return jpa.findByWorkspaceIdAndUserId(workspaceId, userId).map(mapper::toDomain);
    }

    @Override
    public Set<UUID> findWorkspaceIdsByUser(String userId) {
        return jpa.findWorkspaceIdsByUserId(userId);
    }

    @Override
    public long countByWorkspaceAndRole(UUID workspaceId, Role role) {
        return jpa.countByWorkspaceIdAndRole(workspaceId, role);
    }
}
