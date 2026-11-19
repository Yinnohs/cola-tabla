package com.kanbanlive.boards.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** Evento persistido en el log del tablero (payload como JSON ya serializado). */
public record BoardEventRecord(UUID boardId, long seq, String type, String payloadJson, Instant createdAt) {
}
