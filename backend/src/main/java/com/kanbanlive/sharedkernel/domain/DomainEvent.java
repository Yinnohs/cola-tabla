package com.kanbanlive.sharedkernel.domain;

import java.time.Instant;
import java.util.UUID;

/** Hecho inmutable ocurrido en el dominio. Se implementa con records. */
public interface DomainEvent {

    UUID eventId();

    Instant occurredAt();

    String aggregateId();
}
