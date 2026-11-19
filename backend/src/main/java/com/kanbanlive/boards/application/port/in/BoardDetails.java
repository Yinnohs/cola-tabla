package com.kanbanlive.boards.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Modelo de lectura: tablero con sus listas y tarjetas ordenadas por posicion. {@code lastSeq} es el
 * {@code board.event_seq} leido en la misma instantanea que el resto: el estado incluye todos los eventos
 * con {@code seq <= lastSeq} y ninguno posterior (el cliente reanuda desde {@code lastSeq + 1}).
 */
public record BoardDetails(UUID id, UUID workspaceId, String name, long version, long lastSeq,
        List<ListDetails> lists) {

    public record ListDetails(UUID id, String name, String position, long version, List<CardDetails> cards) {
    }

    public record CardDetails(UUID id, String title, String description, String position, long version) {
    }
}
