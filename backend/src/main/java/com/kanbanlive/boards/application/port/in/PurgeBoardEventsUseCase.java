package com.kanbanlive.boards.application.port.in;

import java.time.Duration;

/** Purga acotada del log de eventos mas antiguo que la retencion (B4.3, ADR-0005 / ADR-0008). */
public interface PurgeBoardEventsUseCase {

    /** @return filas borradas en total. */
    int execute(PurgeBoardEventsCommand command);

    /**
     * @param retention  se borran los eventos con created_at anterior a ahora - retention
     * @param batchSize  filas por lote (cada lote es una transaccion corta)
     * @param maxBatches tope de lotes por ejecucion; el resto queda para la siguiente
     */
    record PurgeBoardEventsCommand(Duration retention, int batchSize, int maxBatches) {
    }
}
