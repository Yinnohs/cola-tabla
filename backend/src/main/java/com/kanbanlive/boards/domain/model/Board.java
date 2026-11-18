package com.kanbanlive.boards.domain.model;

import com.kanbanlive.sharedkernel.domain.AggregateRoot;
import com.kanbanlive.sharedkernel.domain.Validations;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Tablero. Referencia a su workspace solo por id. Listas y tarjetas pertenecen a otras capacidades. */
public final class Board extends AggregateRoot<UUID> {

    public static final int NAME_MAX = 120;

    private final UUID workspaceId;
    private final String name;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Board(UUID id, UUID workspaceId, String name, long version, Instant createdAt, Instant updatedAt) {
        super(id, version);
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Board create(UUID id, UUID workspaceId, String name, Instant now) {
        return new Board(id, workspaceId, Validations.requireText(name, "name", NAME_MAX), 0, now, now);
    }

    /** Reconstruccion desde persistencia (sin validar: ya es estado valido). */
    public static Board rehydrate(UUID id, UUID workspaceId, String name, long version, Instant createdAt,
            Instant updatedAt) {
        return new Board(id, workspaceId, name, version, createdAt, updatedAt);
    }

    /** Devuelve un tablero nuevo (inmutable) con el nombre cambiado; conserva la version cargada. */
    public Board rename(String newName, Instant now) {
        return new Board(id(), workspaceId, Validations.requireText(newName, "name", NAME_MAX), version(), createdAt,
                now);
    }

    public UUID workspaceId() {
        return workspaceId;
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
