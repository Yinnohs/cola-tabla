package com.kanbanlive.boards.application.port.out;

import com.kanbanlive.boards.domain.model.Board;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardRepository {

    Board save(Board board);

    Optional<Board> findById(UUID id);

    boolean existsById(UUID id);

    /** Ordenados por fecha de creacion y luego id (estable). */
    List<Board> findByWorkspaceId(UUID workspaceId);
}
