package com.kanbanlive.cards.domain.model;

import com.kanbanlive.sharedkernel.domain.AggregateRoot;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.sharedkernel.domain.Validations;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Tarjeta. Referencia a su lista solo por id; ordenada dentro de la lista por {@link FractionalIndex}. */
public final class Card extends AggregateRoot<UUID> {

    public static final int TITLE_MAX = 255;
    public static final int DESCRIPTION_MAX = 10_000;

    private final UUID listId;
    private final String title;
    private final String description;
    private final FractionalIndex position;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Card(UUID id, UUID listId, String title, String description, FractionalIndex position, long version,
            Instant createdAt, Instant updatedAt) {
        super(id, version);
        this.listId = Objects.requireNonNull(listId, "listId");
        this.title = title;
        this.description = description;
        this.position = Objects.requireNonNull(position, "position");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Card create(UUID id, UUID listId, String title, String description, FractionalIndex position,
            Instant now) {
        return new Card(id, listId, Validations.requireText(title, "title", TITLE_MAX), checked(description),
                position, 0, now, now);
    }

    /** Reconstruccion desde persistencia (sin validar: ya es estado valido). */
    public static Card rehydrate(UUID id, UUID listId, String title, String description, FractionalIndex position,
            long version, Instant createdAt, Instant updatedAt) {
        return new Card(id, listId, title, description, position, version, createdAt, updatedAt);
    }

    /** Reemplaza titulo y descripcion (la descripcion puede ser null). Conserva la version cargada. */
    public Card update(String newTitle, String newDescription, Instant now) {
        return new Card(id(), listId, Validations.requireText(newTitle, "title", TITLE_MAX),
                checked(newDescription), position, version(), createdAt, now);
    }

    /** Cambia lista y posicion; es la unica modificacion de un movimiento (una sola fila). */
    public Card moveTo(UUID newListId, FractionalIndex newPosition, Instant now) {
        return new Card(id(), newListId, title, description, newPosition, version(), createdAt, now);
    }

    private static String checked(String value) {
        if (value != null && value.length() > DESCRIPTION_MAX) {
            throw new ValidationException("description must be at most " + DESCRIPTION_MAX + " characters");
        }
        return value;
    }

    public UUID listId() {
        return listId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public FractionalIndex position() {
        return position;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
