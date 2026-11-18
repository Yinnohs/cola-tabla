package com.kanbanlive.sharedkernel.domain;

/** Conflicto con el estado actual (409). */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super("CONFLICT", message);
    }

    protected ConflictException(String code, String message) {
        super(code, message);
    }
}
