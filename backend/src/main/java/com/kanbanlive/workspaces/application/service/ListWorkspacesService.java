package com.kanbanlive.workspaces.application.service;

import com.kanbanlive.workspaces.application.port.in.ListWorkspacesUseCase;
import com.kanbanlive.workspaces.application.port.in.WorkspaceView;
import com.kanbanlive.workspaces.application.port.out.WorkspaceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ListWorkspacesService implements ListWorkspacesUseCase {

    private final WorkspaceRepository repository;

    public ListWorkspacesService(WorkspaceRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<WorkspaceView> execute() {
        return repository.findAll().stream().map(WorkspaceViews::of).toList();
    }
}
