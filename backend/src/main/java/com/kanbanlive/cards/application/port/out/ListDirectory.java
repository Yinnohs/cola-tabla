package com.kanbanlive.cards.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Pregunta a la capacidad lists (via su port.in) si una lista existe. */
public interface ListDirectory {

    boolean exists(UUID listId);

    /** Tablero al que pertenece la lista (vacio si la lista no existe). */
    Optional<UUID> boardIdOf(UUID listId);
}
