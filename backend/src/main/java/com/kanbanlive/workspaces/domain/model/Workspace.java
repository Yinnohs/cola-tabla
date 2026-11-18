package com.kanbanlive.workspaces.domain.model;

import com.kanbanlive.sharedkernel.domain.AggregateRoot;
import com.kanbanlive.sharedkernel.domain.Validations;
import java.time.Instant;
import java.util.UUID;

/** Espacio de trabajo: contenedor de tableros. (La membresia llega en la Fase 2.) */
public final class Workspace extends AggregateRoot<UUID> {

    public static final int NAME_MAX = 120;

    private final String name;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Workspace(UUID id, String name, long version, Instant createdAt, Instant updatedAt) {
        super(id, version);
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Workspace create(UUID id, String name, Instant now) {
        return new Workspace(id, Validations.requireText(name, "name", NAME_MAX), 0, now, now);
    }

    /** Reconstruccion desde persistencia (sin validar: ya es estado valido). */
    public static Workspace rehydrate(UUID id, String name, long version, Instant createdAt, Instant updatedAt) {
        return new Workspace(id, name, version, createdAt, updatedAt);
    }

    public String name() {
        return name;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
