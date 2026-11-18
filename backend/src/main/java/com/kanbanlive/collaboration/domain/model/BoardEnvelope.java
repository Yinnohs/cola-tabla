package com.kanbanlive.collaboration.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Sobre de cada evento que viaja por {@code /topic/boards.{boardId}}. {@code seq} es el contador por tablero
 * (ADR-0008); {@code clientId} es null si el origen no lo envio; {@code payload} se serializa como objeto JSON.
 * Contrato: docs/contracts/board-events.md.
 */
public record BoardEnvelope(UUID boardId, long seq, String type, Object payload, String clientId,
        Instant occurredAt, int schemaVersion) {

    public static final int SCHEMA_VERSION = 1;
}
