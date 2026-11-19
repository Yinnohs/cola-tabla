package com.kanbanlive.collaboration.domain.error;

import com.kanbanlive.sharedkernel.domain.GoneException;

/** Faltan demasiados eventos o el log fue purgado: el cliente debe recargar el tablero completo (410). */
public class EventsGoneException extends GoneException {

    public static final String CODE = "EVENTS_GONE";

    public EventsGoneException(String message) {
        super(CODE, message);
    }
}
