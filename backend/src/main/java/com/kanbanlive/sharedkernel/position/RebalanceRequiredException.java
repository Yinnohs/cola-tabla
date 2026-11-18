package com.kanbanlive.sharedkernel.position;

import com.kanbanlive.sharedkernel.domain.ConflictException;

/**
 * La posicion calculada entre dos vecinas ya es demasiado larga ({@link FractionalIndex#isExhausted()}): hay que
 * rebalancear la lista/columna antes de insertar o mover (ADR-0003). Los servicios de aplicacion que crean/mueven
 * la capturan, rebalancean los hermanos y reintentan una vez (B1.7); si el reintento tambien se agota
 * se traduce a 409 con code {@code REBALANCE_REQUIRED}.
 */
public class RebalanceRequiredException extends ConflictException {

    public RebalanceRequiredException(String message) {
        super("REBALANCE_REQUIRED", message);
    }
}
