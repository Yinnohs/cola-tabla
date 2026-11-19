package com.kanbanlive.collaboration.application.port.in;

import com.kanbanlive.collaboration.domain.model.BoardEnvelope;
import java.util.List;
import java.util.UUID;

/**
 * Resincronizacion por seq (B4.1/B4.2): eventos del tablero posteriores a {@code since}. Errores: 400
 * ({@code InvalidEventCursorException}) si since es negativo o supera el seq actual; 410
 * ({@code EventsGoneException}) si faltan mas de max-gap eventos o el log fue purgado.
 */
public interface GetBoardEventsSinceUseCase {

    BoardEventsSince execute(UUID boardId, long since);

    /** @param lastSeq seq actual del tablero (== since si no hay nada nuevo); events en orden de seq. */
    record BoardEventsSince(UUID boardId, long since, long lastSeq, List<BoardEnvelope> events) {
    }
}
