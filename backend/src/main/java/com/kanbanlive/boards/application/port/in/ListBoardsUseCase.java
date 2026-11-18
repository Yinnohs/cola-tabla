package com.kanbanlive.boards.application.port.in;

import java.util.List;
import java.util.UUID;

public interface ListBoardsUseCase {

    /** Tableros del workspace ordenados por creacion y luego id. NotFoundException si el workspace no existe. */
    List<BoardSummary> execute(UUID workspaceId);
}
