package com.kanbanlive.sharedkernel.domain;

/** El recurso existio pero ya no esta disponible (410): el cliente debe recargar el estado completo. */
public class GoneException extends DomainException {

    public GoneException(String message) {
        super("GONE", message);
    }

    protected GoneException(String code, String message) {
        super(code, message);
    }
}
