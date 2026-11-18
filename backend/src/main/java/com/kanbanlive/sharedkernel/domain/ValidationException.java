package com.kanbanlive.sharedkernel.domain;

/** Invariante de dominio violada (422). */
public class ValidationException extends DomainException {

    public ValidationException(String message) {
        super("VALIDATION", message);
    }
}
