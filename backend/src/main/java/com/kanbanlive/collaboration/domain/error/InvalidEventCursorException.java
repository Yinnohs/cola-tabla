package com.kanbanlive.collaboration.domain.error;

import com.kanbanlive.sharedkernel.domain.BadRequestException;

/** El cursor {@code since} es negativo o esta por delante del ultimo seq del tablero (400). */
public class InvalidEventCursorException extends BadRequestException {

    public static final String CODE = "INVALID_SINCE";

    public InvalidEventCursorException(String message) {
        super(CODE, message);
    }
}
