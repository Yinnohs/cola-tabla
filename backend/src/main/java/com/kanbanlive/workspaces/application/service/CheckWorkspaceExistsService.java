package com.kanbanlive.workspaces.application.service;

import com.kanbanlive.workspaces.application.port.in.CheckWorkspaceExistsUseCase;
import com.kanbanlive.workspaces.application.port.out.WorkspaceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CheckWorkspaceExistsService implements CheckWorkspaceExistsUseCase {

    private final WorkspaceRepository repository;

    public CheckWorkspaceExistsService(WorkspaceRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean execute(UUID workspaceId) {
        return repository.existsById(workspaceId);
    }
}
