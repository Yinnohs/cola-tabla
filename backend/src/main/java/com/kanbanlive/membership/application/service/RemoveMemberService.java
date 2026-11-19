package com.kanbanlive.membership.application.service;

import com.kanbanlive.membership.application.port.in.RemoveMemberUseCase;
import com.kanbanlive.membership.application.port.out.MembershipRepository;
import com.kanbanlive.membership.domain.error.LastOwnerException;
import com.kanbanlive.membership.domain.model.Membership;
import com.kanbanlive.membership.domain.model.Role;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RemoveMemberService implements RemoveMemberUseCase {

    private final MembershipRepository repository;

    public RemoveMemberService(MembershipRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(UUID workspaceId, String userId) {
        Membership membership = repository.findByWorkspaceAndUser(workspaceId, userId)
                .orElseThrow(() -> new NotFoundException("membership not found"));
        if (membership.role() == Role.OWNER && repository.countByWorkspaceAndRole(workspaceId, Role.OWNER) <= 1) {
            throw new LastOwnerException("a workspace must keep at least one OWNER");
        }
        repository.delete(membership);
    }
}
