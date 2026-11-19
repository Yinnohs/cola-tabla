package com.kanbanlive.boards.application.port.out;

import com.kanbanlive.boards.application.port.in.BoardEventRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Log de eventos por tablero. {@link #append} debe ejecutarse dentro de la transaccion del caso de uso que
 * modifica el tablero: asigna el siguiente {@code seq} del tablero y guarda la fila en {@code board_event}.
 */
public interface BoardEventLog {

    /** @param payloadJson JSON ya serializado. @return el seq asignado (1, 2, 3... por tablero, sin huecos). */
    long append(UUID boardId, String type, String payloadJson);

    /** Ultimo seq asignado, o vacio si el tablero no existe. */
    Optional<Long> currentSeq(UUID boardId);

    /** Eventos retenidos con {@code since < seq <= upTo}, por seq ascendente, maximo {@code limit}. */
    List<BoardEventRecord> findAfter(UUID boardId, long since, long upTo, int limit);

    /** Borra como maximo {@code batchSize} eventos con created_at anterior a {@code cutoff}, en su propia transaccion. */
    int deleteOlderThan(Instant cutoff, int batchSize);
}
