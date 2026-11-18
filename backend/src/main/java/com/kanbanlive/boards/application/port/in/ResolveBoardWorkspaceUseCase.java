package com.kanbanlive.boards.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Puerto de entrada que otras capacidades (p. ej. membership) usan para saber a que workspace pertenece un tablero. */
public interface ResolveBoardWorkspaceUseCase {

    Optional<UUID> execute(UUID boardId);
}
