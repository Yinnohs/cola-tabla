package com.kanbanlive.boards.application.port.in;

import java.util.UUID;

/** Puerto de entrada que otras capacidades (p. ej. lists) usan para validar la existencia de un tablero. */
public interface CheckBoardExistsUseCase {

    boolean execute(UUID boardId);
}
