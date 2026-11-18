package com.kanbanlive.lists.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Entidad JPA (no es el agregado). {@code boardId} es un UUID plano, sin asociacion JPA. */
@Entity
@Table(name = "board_list")
public class ListJpaEntity {

    @Id
    private UUID id;

    @Column(name = "board_id", nullable = false, updatable = false)
    private UUID boardId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String position;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ListJpaEntity() {
    }

    ListJpaEntity(UUID id, UUID boardId, String name, String position, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.boardId = boardId;
        this.name = name;
        this.position = position;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getBoardId() {
        return boardId;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    String getPosition() {
        return position;
    }

    void setPosition(String position) {
        this.position = position;
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
