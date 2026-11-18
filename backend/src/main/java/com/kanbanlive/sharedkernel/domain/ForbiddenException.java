package com.kanbanlive.sharedkernel.domain;

/** Operacion no permitida (403). */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
