package com.kanbanlive.lists.application.port.out;

import com.kanbanlive.lists.domain.model.BoardList;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import com.kanbanlive.sharedkernel.position.PositionAssignment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListRepository {

    BoardList save(BoardList list);

    /**
     * Persiste un movimiento (posicion) con last-write-wins: UPDATE directo de una fila SIN comprobar la version
     * cargada y con version+1 (ADR-0005). Nunca lanza StaleVersionException por concurrencia.
     */
    BoardList saveMove(BoardList list);

    Optional<BoardList> findById(UUID id);

    boolean existsById(UUID id);

    /** Posicion de la ultima lista del tablero, o vacio si no tiene listas. */
    Optional<FractionalIndex> findLastPosition(UUID boardId);

    /** Posicion de la lista {@code listId} solo si pertenece al tablero {@code boardId}. */
    Optional<FractionalIndex> findPositionInBoard(UUID boardId, UUID listId);

    /** Ids de las lists de board en orden de posicion (para rebalancear). */
    List<UUID> findIdsInOrder(UUID boardId);

    /** Reescribe las posiciones indicadas (rebalanceo): cada fila incrementa su version. Dentro de la transaccion actual. */
    void updatePositions(List<PositionAssignment> assignments, Instant now);
}
