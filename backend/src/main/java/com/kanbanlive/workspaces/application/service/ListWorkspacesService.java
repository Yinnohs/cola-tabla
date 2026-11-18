package com.kanbanlive.workspaces.application.service;

import com.kanbanlive.sharedkernel.application.CurrentUser;
import com.kanbanlive.workspaces.application.port.in.ListWorkspacesUseCase;
import com.kanbanlive.workspaces.application.port.in.WorkspaceView;
import com.kanbanlive.workspaces.application.port.out.WorkspaceAccess;
import com.kanbanlive.workspaces.application.port.out.WorkspaceRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListWorkspacesService implements ListWorkspacesUseCase {

    private final WorkspaceRepository repository;
    private final CurrentUser currentUser;
    private final WorkspaceAccess access;

    public ListWorkspacesService(WorkspaceRepository repository, CurrentUser currentUser, WorkspaceAccess access) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.access = access;
    }

    /** Solo los workspaces donde el usuario actual es miembro (todos si la seguridad esta desactivada en local). */
    @Override
    public List<WorkspaceView> execute() {
        if (currentUser.unrestricted()) {
            return repository.findAll().stream().map(WorkspaceViews::of).toList();
        }
        Set<UUID> visible = access.workspaceIdsOf(currentUser.id());
        return repository.findAll().stream().filter(w -> visible.contains(w.id())).map(WorkspaceViews::of)
                .toList();
    }
}
