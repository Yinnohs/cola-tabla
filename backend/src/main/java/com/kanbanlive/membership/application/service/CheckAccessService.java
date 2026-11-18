package com.kanbanlive.membership.application.service;

import com.kanbanlive.membership.application.port.in.CheckAccessUseCase;
import com.kanbanlive.membership.application.port.out.ResourceWorkspaceLookup;
import com.kanbanlive.membership.application.port.out.MembershipRepository;
import com.kanbanlive.membership.domain.model.Role;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CheckAccessService implements CheckAccessUseCase {

    private final MembershipRepository repository;
    private final ResourceWorkspaceLookup resources;

    public CheckAccessService(MembershipRepository repository, ResourceWorkspaceLookup resources) {
        this.repository = repository;
        this.resources = resources;
    }

    @Override
    public boolean execute(String userId, UUID resourceId, Permission permission) {
        if (userId == null || resourceId == null || permission == null) {
            return false;
        }
        // Los ids son UUID aleatorios: se resuelve el workspace del recurso; si no hay, se trata como workspaceId.
        UUID workspaceId = resources.workspaceOf(resourceId).orElse(resourceId);
        return repository.findByWorkspaceAndUser(workspaceId, userId)
                .map(m -> allows(m.role(), permission))
                .orElse(false);
    }

    private static boolean allows(Role role, Permission permission) {
        return switch (permission) {
            case VIEW -> role.canView();
            case EDIT -> role.canEdit();
            case ADMIN -> role.canAdmin();
        };
    }
}
