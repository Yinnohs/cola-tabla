package com.kanbanlive.workspaces.application.service;

import com.kanbanlive.sharedkernel.application.CurrentUser;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.workspaces.application.port.in.GetWorkspaceUseCase;
import com.kanbanlive.workspaces.application.port.in.WorkspaceView;
import com.kanbanlive.workspaces.application.port.out.WorkspaceAccess;
import com.kanbanlive.workspaces.application.port.out.WorkspaceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetWorkspaceService implements GetWorkspaceUseCase {

    private final WorkspaceRepository repository;
    private final CurrentUser currentUser;
    private final WorkspaceAccess access;

    public GetWorkspaceService(WorkspaceRepository repository, CurrentUser currentUser, WorkspaceAccess access) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.access = access;
    }

    @Override
    public WorkspaceView execute(UUID workspaceId) {
        return repository.findById(workspaceId).map(w -> WorkspaceViews.of(w, roleOf(workspaceId)))
                .orElseThrow(() -> new NotFoundException("workspace " + workspaceId + " not found"));
    }

    /** Rol del usuario actual (OWNER si la seguridad esta desactivada en local). */
    private String roleOf(UUID workspaceId) {
        if (currentUser.unrestricted()) {
            return "OWNER";
        }
        return access.rolesOf(currentUser.id()).get(workspaceId);
    }
}
