package com.kanbanlive.workspaces.application.port.in;

import java.util.List;

public interface ListWorkspacesUseCase {

    /** Fase 1: todos los workspaces (sin membresia). En la Fase 2 se filtra por usuario. */
    List<WorkspaceView> execute();
}
