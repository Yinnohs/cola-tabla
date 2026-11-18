package com.kanbanlive.cards.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Entidad JPA (no es el agregado). {@code listId} es un UUID plano, sin asociacion JPA. */
@Entity
@Table(name = "card")
public class CardJpaEntity {

    @Id
    private UUID id;

    @Column(name = "list_id", nullable = false)
    private UUID listId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private String position;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CardJpaEntity() {
    }

    CardJpaEntity(UUID id, UUID listId, String title, String description, String position, Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.listId = listId;
        this.title = title;
        this.description = description;
        this.position = position;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getListId() {
        return listId;
    }

    void setListId(UUID listId) {
        this.listId = listId;
    }

    String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    String getDescription() {
        return description;
    }

    void setDescription(String description) {
        this.description = description;
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
