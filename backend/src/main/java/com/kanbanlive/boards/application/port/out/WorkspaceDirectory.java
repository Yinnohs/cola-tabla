package com.kanbanlive.boards.application.port.out;

import java.util.UUID;

/** Pregunta a la capacidad workspaces (via su port.in) si un workspace existe. */
public interface WorkspaceDirectory {

    boolean exists(UUID workspaceId);
}
