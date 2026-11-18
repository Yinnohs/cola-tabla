package com.kanbanlive.sharedkernel.domain;

/** Version obsoleta (concurrencia optimista). Subtipo de conflicto: 409. */
public class StaleVersionException extends ConflictException {

    public StaleVersionException(String message) {
        super("STALE_VERSION", message);
    }
}
