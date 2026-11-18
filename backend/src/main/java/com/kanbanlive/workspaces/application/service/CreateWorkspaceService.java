package com.kanbanlive.workspaces.application.service;

import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.application.IdGenerator;
import com.kanbanlive.workspaces.application.port.in.CreateWorkspaceUseCase;
import com.kanbanlive.workspaces.application.port.in.WorkspaceView;
import com.kanbanlive.workspaces.application.port.out.WorkspaceRepository;
import com.kanbanlive.workspaces.domain.model.Workspace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateWorkspaceService implements CreateWorkspaceUseCase {

    private final WorkspaceRepository repository;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateWorkspaceService(WorkspaceRepository repository, IdGenerator ids, Clock clock) {
        this.repository = repository;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public WorkspaceView execute(CreateWorkspaceCommand command) {
        Workspace workspace = Workspace.create(ids.newId(), command.name(), clock.now());
        return WorkspaceViews.of(repository.save(workspace));
    }
}
