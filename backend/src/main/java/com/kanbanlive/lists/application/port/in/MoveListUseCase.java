package com.kanbanlive.lists.application.port.in;

import com.kanbanlive.sharedkernel.application.Command;
import java.util.UUID;

public interface MoveListUseCase {

    /** Reposiciona la lista actualizando solo su propia fila. */
    ListSummary execute(MoveListCommand command);

    /**
     * Nueva posicion entre las listas vecinas {@code prevListId} y {@code nextListId} (del mismo tablero, ambas
     * opcionales: null = extremo). Para una posicion exacta conviene indicar las dos vecinas.
     */
    record MoveListCommand(UUID listId, UUID prevListId, UUID nextListId) implements Command {
    }
}
