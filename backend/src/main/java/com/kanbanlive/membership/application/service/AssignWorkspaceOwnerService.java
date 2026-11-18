package com.kanbanlive.membership.application.service;

import com.kanbanlive.membership.application.port.in.AssignWorkspaceOwnerUseCase;
import com.kanbanlive.membership.application.port.out.MembershipRepository;
import com.kanbanlive.membership.domain.model.Membership;
import com.kanbanlive.membership.domain.model.Role;
import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.application.IdGenerator;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssignWorkspaceOwnerService implements AssignWorkspaceOwnerUseCase {

    private final MembershipRepository repository;
    private final IdGenerator ids;
    private final Clock clock;

    public AssignWorkspaceOwnerService(MembershipRepository repository, IdGenerator ids, Clock clock) {
        this.repository = repository;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public void execute(UUID workspaceId, String userId) {
        Membership existing = repository.findByWorkspaceAndUser(workspaceId, userId).orElse(null);
        if (existing == null) {
            repository.save(Membership.create(ids.newId(), workspaceId, userId, Role.OWNER, clock.now()));
        } else if (existing.role() != Role.OWNER) {
            existing.changeRole(Role.OWNER, clock.now());
            repository.save(existing);
        }
    }
}
