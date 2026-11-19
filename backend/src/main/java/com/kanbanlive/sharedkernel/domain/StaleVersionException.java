package com.kanbanlive.sharedkernel.domain;

/**
 * Version obsoleta (concurrencia optimista). Subtipo de conflicto: 409. Puede transportar el estado actual del
 * recurso ({@link #current()}, un objeto opaco para el dominio: normalmente el resumen del caso de uso) para que
 * el cliente pueda comparar y resolver (ADR-0005). Es null cuando no se pudo leer (p. ej. carrera detectada al
 * escribir).
 */
public class StaleVersionException extends ConflictException {

    private final transient Object current;

    public StaleVersionException(String message) {
        this(message, null);
    }

    public StaleVersionException(String message, Object current) {
        super("STALE_VERSION", message);
        this.current = current;
    }

    /** Estado actual del recurso, o null si no esta disponible. */
    public Object current() {
        return current;
    }
}
