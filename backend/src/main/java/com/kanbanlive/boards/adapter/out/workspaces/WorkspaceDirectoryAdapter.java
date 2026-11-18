package com.kanbanlive.boards.adapter.out.workspaces;

import com.kanbanlive.boards.application.port.out.WorkspaceDirectory;
import com.kanbanlive.workspaces.application.port.in.CheckWorkspaceExistsUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Cruce entre capacidades permitido: boards -> workspaces SOLO via su port.in. */
@Component
class WorkspaceDirectoryAdapter implements WorkspaceDirectory {

    private final CheckWorkspaceExistsUseCase checkWorkspaceExists;

    WorkspaceDirectoryAdapter(CheckWorkspaceExistsUseCase checkWorkspaceExists) {
        this.checkWorkspaceExists = checkWorkspaceExists;
    }

    @Override
    public boolean exists(UUID workspaceId) {
        return checkWorkspaceExists.execute(workspaceId);
    }
}
