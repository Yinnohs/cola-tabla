package com.kanbanlive.lists.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Puerto de entrada que otras capacidades (p. ej. membership) usan para saber a que tablero pertenece una lista. */
public interface ResolveListBoardUseCase {

    Optional<UUID> execute(UUID listId);
}
