package com.kanbanlive.sharedkernel.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Raiz de agregado: identidad, version de concurrencia optimista (reflejo del Version
 * de la entidad JPA, que vive solo en el adapter) y eventos pendientes de publicar.
 */
public abstract class AggregateRoot<ID> extends Entity<ID> {

    private final long version;
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    protected AggregateRoot(ID id, long version) {
        super(id);
        this.version = version;
    }

    public long version() {
        return version;
    }

    protected void registerEvent(DomainEvent event) {
        pendingEvents.add(Objects.requireNonNull(event, "event"));
    }

    /** Devuelve los eventos pendientes y vacia la lista. */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }
}
