package com.kanbanlive.membership.application.service;

import com.kanbanlive.membership.application.port.in.InviteMemberUseCase;
import com.kanbanlive.membership.application.port.in.MembershipView;
import com.kanbanlive.membership.application.port.out.MembershipRepository;
import com.kanbanlive.membership.domain.error.AlreadyMemberException;
import com.kanbanlive.membership.domain.model.Membership;
import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.application.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InviteMemberService implements InviteMemberUseCase {

    private final MembershipRepository repository;
    private final IdGenerator ids;
    private final Clock clock;

    public InviteMemberService(MembershipRepository repository, IdGenerator ids, Clock clock) {
        this.repository = repository;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public MembershipView execute(InviteMemberCommand c) {
        Membership membership = Membership.create(ids.newId(), c.workspaceId(), c.userId(),
                Roles.parse(c.role()), clock.now());
        if (repository.findByWorkspaceAndUser(membership.workspaceId(), membership.userId()).isPresent()) {
            throw new AlreadyMemberException("user is already a member of the workspace");
        }
        return MembershipViews.of(repository.save(membership));
    }
}
