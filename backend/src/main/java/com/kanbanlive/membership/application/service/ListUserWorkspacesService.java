package com.kanbanlive.membership.application.service;

import com.kanbanlive.membership.application.port.in.ListUserWorkspacesUseCase;
import com.kanbanlive.membership.application.port.out.MembershipRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListUserWorkspacesService implements ListUserWorkspacesUseCase {

    private final MembershipRepository repository;

    public ListUserWorkspacesService(MembershipRepository repository) {
        this.repository = repository;
    }

    @Override
    public Set<UUID> execute(String userId) {
        return repository.findWorkspaceIdsByUser(userId);
    }
}
