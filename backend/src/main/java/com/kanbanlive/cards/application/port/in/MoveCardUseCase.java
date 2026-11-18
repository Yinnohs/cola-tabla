package com.kanbanlive.cards.application.port.in;

import com.kanbanlive.sharedkernel.application.Command;
import java.util.UUID;

public interface MoveCardUseCase {

    /** Mueve la tarjeta con un unico UPDATE sobre su fila; las vecinas no se tocan. */
    CardSummary execute(MoveCardCommand command);

    /**
     * Mueve a {@code targetListId} (puede ser la misma lista) entre las tarjetas vecinas {@code prevCardId} y
     * {@code nextCardId}, que deben estar en la lista destino (ambas opcionales: null = extremo).
     */
    record MoveCardCommand(UUID cardId, UUID targetListId, UUID prevCardId, UUID nextCardId) implements Command {
    }
}
