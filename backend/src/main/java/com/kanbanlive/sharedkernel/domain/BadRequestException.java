package com.kanbanlive.sharedkernel.domain;

/** Peticion semanticamente invalida respecto al estado (400), p. ej. un cursor fuera de rango. */
public class BadRequestException extends DomainException {

    public BadRequestException(String message) {
        super("BAD_REQUEST", message);
    }

    protected BadRequestException(String code, String message) {
        super(code, message);
    }
}
