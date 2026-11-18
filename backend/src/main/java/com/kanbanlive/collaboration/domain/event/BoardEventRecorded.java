package com.kanbanlive.collaboration.domain.event;

import com.kanbanlive.collaboration.domain.model.BoardEnvelope;
import com.kanbanlive.sharedkernel.domain.DomainEvent;
import java.time.Instant;
import java.util.UUID;

/** Un evento de tablero ya registrado (seq asignado) en la transaccion; se difunde tras el commit. */
public record BoardEventRecorded(UUID eventId, Instant occurredAt, BoardEnvelope envelope) implements DomainEvent {

    @Override
    public String aggregateId() {
        return envelope.boardId().toString();
    }
}
