package com.kanbanlive.boards.application.port.in;

import java.util.List;
import java.util.UUID;

/** Lectura del log de eventos de un tablero (resync por seq, Fase 4). Lo usa collaboration. */
public interface ReadBoardEventLogUseCase {

    /**
     * Ultimo seq asignado al tablero (0 si no tiene eventos).
     *
     * @throws com.kanbanlive.sharedkernel.domain.NotFoundException si el tablero no existe
     */
    long currentSeq(UUID boardId);

    /** Eventos retenidos con {@code since < seq <= upTo}, en orden de seq, como maximo {@code limit}. */
    List<BoardEventRecord> eventsAfter(UUID boardId, long since, long upTo, int limit);
}
