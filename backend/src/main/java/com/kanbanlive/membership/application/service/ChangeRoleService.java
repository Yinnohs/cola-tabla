package com.kanbanlive.membership.application.service;

import com.kanbanlive.membership.application.port.in.ChangeRoleUseCase;
import com.kanbanlive.membership.application.port.in.MembershipView;
import com.kanbanlive.membership.application.port.out.MembershipRepository;
import com.kanbanlive.membership.domain.error.LastOwnerException;
import com.kanbanlive.membership.domain.model.Membership;
import com.kanbanlive.membership.domain.model.Role;
import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChangeRoleService implements ChangeRoleUseCase {

    private final MembershipRepository repository;
    private final Clock clock;

    public ChangeRoleService(MembershipRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public MembershipView execute(ChangeRoleCommand c) {
        Role newRole = Roles.parse(c.role());
        Membership membership = repository.findByWorkspaceAndUser(c.workspaceId(), c.userId())
                .orElseThrow(() -> new NotFoundException("membership not found"));
        if (membership.role() == Role.OWNER && newRole != Role.OWNER
                && repository.countByWorkspaceAndRole(c.workspaceId(), Role.OWNER) <= 1) {
            throw new LastOwnerException("a workspace must keep at least one OWNER");
        }
        membership.changeRole(newRole, clock.now());
        return MembershipViews.of(repository.save(membership));
    }
}
