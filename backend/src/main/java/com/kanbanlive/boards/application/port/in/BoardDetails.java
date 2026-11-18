package com.kanbanlive.boards.application.port.in;

import java.util.List;
import java.util.UUID;

/** Modelo de lectura: tablero con sus listas y tarjetas ordenadas por posicion. */
public record BoardDetails(UUID id, UUID workspaceId, String name, long version, List<ListDetails> lists) {

    public record ListDetails(UUID id, String name, String position, long version, List<CardDetails> cards) {
    }

    public record CardDetails(UUID id, String title, String description, String position, long version) {
    }
}
