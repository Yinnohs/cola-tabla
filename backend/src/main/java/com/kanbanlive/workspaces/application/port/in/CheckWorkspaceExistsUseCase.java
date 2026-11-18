package com.kanbanlive.workspaces.application.port.in;

import java.util.UUID;

/** Puerto de entrada que otras capacidades (p. ej. boards) usan para validar la existencia de un workspace. */
public interface CheckWorkspaceExistsUseCase {

    boolean execute(UUID workspaceId);
}
