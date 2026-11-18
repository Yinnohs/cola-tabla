package com.kanbanlive.boards.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Entidad JPA (no es el agregado). {@code workspaceId} es un UUID plano, sin asociacion JPA. */
@Entity
@Table(name = "board")
public class BoardJpaEntity {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String name;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BoardJpaEntity() {
    }

    BoardJpaEntity(UUID id, UUID workspaceId, String name, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getWorkspaceId() {
        return workspaceId;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    Long getVersion() {
        return version;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
