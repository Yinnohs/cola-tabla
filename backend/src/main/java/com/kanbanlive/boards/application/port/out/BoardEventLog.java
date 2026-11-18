package com.kanbanlive.boards.application.port.out;

import java.util.UUID;

/**
 * Log de eventos por tablero. {@link #append} debe ejecutarse dentro de la transaccion del caso de uso que
 * modifica el tablero: asigna el siguiente {@code seq} del tablero y guarda la fila en {@code board_event}.
 */
public interface BoardEventLog {

    /** @param payloadJson JSON ya serializado. @return el seq asignado (1, 2, 3... por tablero, sin huecos). */
    long append(UUID boardId, String type, String payloadJson);
}
