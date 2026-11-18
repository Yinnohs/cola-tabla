package com.kanbanlive.boards.application.port.in;

import java.util.UUID;

/**
 * Registra un evento en el log del tablero asignandole el siguiente seq. Exige una transaccion en curso (la del
 * caso de uso que origina el cambio); lo usan boards, lists y cards.
 */
public interface RecordBoardEventUseCase {

    /** @return el seq asignado al evento dentro del tablero. */
    long execute(UUID boardId, String type, String payloadJson);
}
