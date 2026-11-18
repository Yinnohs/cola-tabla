package com.kanbanlive.boards.application.port.out;

import com.kanbanlive.boards.application.port.in.BoardDetails;
import java.util.Optional;
import java.util.UUID;

/** Puerto de lectura (CQRS ligero): proyeccion de solo lectura de tablero + listas + tarjetas. */
public interface BoardDetailsReader {

    Optional<BoardDetails> findById(UUID boardId);
}
