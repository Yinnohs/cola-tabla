package com.kanbanlive.sharedkernel.domain;

/** Base de errores de negocio. platform.web las traduce a ProblemDetail (404/409/403/422). */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
