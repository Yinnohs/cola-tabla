package com.kanbanlive.workspaces.application.port.in;

import com.kanbanlive.sharedkernel.application.Command;

public interface CreateWorkspaceUseCase {

    WorkspaceView execute(CreateWorkspaceCommand command);

    record CreateWorkspaceCommand(String name) implements Command {
    }
}
