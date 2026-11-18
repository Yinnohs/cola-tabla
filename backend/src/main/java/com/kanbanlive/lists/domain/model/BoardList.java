package com.kanbanlive.lists.domain.model;

import com.kanbanlive.sharedkernel.domain.AggregateRoot;
import com.kanbanlive.sharedkernel.domain.Validations;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Lista (columna) de un tablero. Referencia a su tablero solo por id; ordenada por {@link FractionalIndex}. */
public final class BoardList extends AggregateRoot<UUID> {

    public static final int NAME_MAX = 120;

    private final UUID boardId;
    private final String name;
    private final FractionalIndex position;
    private final Instant createdAt;
    private final Instant updatedAt;

    private BoardList(UUID id, UUID boardId, String name, FractionalIndex position, long version, Instant createdAt,
            Instant updatedAt) {
        super(id, version);
        this.boardId = Objects.requireNonNull(boardId, "boardId");
        this.name = name;
        this.position = Objects.requireNonNull(position, "position");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static BoardList create(UUID id, UUID boardId, String name, FractionalIndex position, Instant now) {
        return new BoardList(id, boardId, Validations.requireText(name, "name", NAME_MAX), position, 0, now, now);
    }

    /** Reconstruccion desde persistencia (sin validar: ya es estado valido). */
    public static BoardList rehydrate(UUID id, UUID boardId, String name, FractionalIndex position, long version,
            Instant createdAt, Instant updatedAt) {
        return new BoardList(id, boardId, name, position, version, createdAt, updatedAt);
    }

    public BoardList rename(String newName, Instant now) {
        return new BoardList(id(), boardId, Validations.requireText(newName, "name", NAME_MAX), position, version(),
                createdAt, now);
    }

    /** Solo cambia la posicion de esta lista; las vecinas no se tocan. */
    public BoardList moveTo(FractionalIndex newPosition, Instant now) {
        return new BoardList(id(), boardId, name, newPosition, version(), createdAt, now);
    }

    public UUID boardId() {
        return boardId;
    }

    public String name() {
        return name;
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
