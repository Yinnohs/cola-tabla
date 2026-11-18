package com.kanbanlive.workspaces.application.service;

import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.workspaces.application.port.in.GetWorkspaceUseCase;
import com.kanbanlive.workspaces.application.port.in.WorkspaceView;
import com.kanbanlive.workspaces.application.port.out.WorkspaceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GetWorkspaceService implements GetWorkspaceUseCase {

    private final WorkspaceRepository repository;

    public GetWorkspaceService(WorkspaceRepository repository) {
        this.repository = repository;
    }

    @Override
    public WorkspaceView execute(UUID workspaceId) {
        return repository.findById(workspaceId).map(WorkspaceViews::of)
                .orElseThrow(() -> new NotFoundException("workspace " + workspaceId + " not found"));
    }
}
