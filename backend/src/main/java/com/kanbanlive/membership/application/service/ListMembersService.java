package com.kanbanlive.membership.application.service;

import com.kanbanlive.membership.application.port.in.ListMembersUseCase;
import com.kanbanlive.membership.application.port.in.MembershipView;
import com.kanbanlive.membership.application.port.out.MembershipRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListMembersService implements ListMembersUseCase {

    private final MembershipRepository repository;

    public ListMembersService(MembershipRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<MembershipView> execute(UUID workspaceId) {
        return repository.findByWorkspace(workspaceId).stream().map(MembershipViews::of).toList();
    }
}
