package com.kanbanlive.lists.application.port.out;

import java.util.UUID;

/** Pregunta a la capacidad boards (via su port.in) si un tablero existe. */
public interface BoardDirectory {

    boolean exists(UUID boardId);
}
