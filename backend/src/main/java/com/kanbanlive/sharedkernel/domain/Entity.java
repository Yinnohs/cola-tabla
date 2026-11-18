package com.kanbanlive.sharedkernel.domain;

import java.util.Objects;

/** Igualdad por identidad (mismo tipo concreto e id). */
public abstract class Entity<ID> {

    private final ID id;

    protected Entity(ID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public ID id() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return id.equals(((Entity<?>) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), id);
    }
}
