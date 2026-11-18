package com.kanbanlive.membership.adapter.out.persistence;

import com.kanbanlive.membership.domain.model.Membership;
import org.springframework.stereotype.Component;

@Component
class MembershipEntityMapper {

    Membership toDomain(MembershipJpaEntity e) {
        long version = e.getVersion() == null ? 0 : e.getVersion();
        return Membership.rehydrate(e.getId(), e.getWorkspaceId(), e.getUserId(), e.getRole(), version,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    MembershipJpaEntity toNewEntity(Membership m) {
        return new MembershipJpaEntity(m.id(), m.workspaceId(), m.userId(), m.role(), m.createdAt(), m.updatedAt());
    }

    void apply(Membership m, MembershipJpaEntity e) {
        e.setRole(m.role());
        e.setUpdatedAt(m.updatedAt());
    }
}
