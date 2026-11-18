package com.kanbanlive.sharedkernel.domain;

/** Recurso inexistente (404). */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
