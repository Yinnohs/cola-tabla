package com.kanbanlive.membership.domain.model;

/** Rol de un usuario dentro de un workspace. Cada rol incluye los permisos de los inferiores. */
public enum Role {
    VIEWER, EDITOR, OWNER;

    public boolean canView() {
        return true;
    }

    public boolean canEdit() {
        return this == EDITOR || this == OWNER;
    }

    public boolean canAdmin() {
        return this == OWNER;
    }
}
