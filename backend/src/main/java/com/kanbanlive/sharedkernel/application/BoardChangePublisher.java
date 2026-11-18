package com.kanbanlive.sharedkernel.application;

import java.util.UUID;

/**
 * Puerto de salida de los casos de uso que modifican un tablero (boards, lists, cards): registra el cambio como
 * evento del tablero (seq + fila en board_event) DENTRO de la transaccion en curso y programa su difusion para
 * DESPUES del commit. Lo implementa la capacidad collaboration.
 */
public interface BoardChangePublisher {

    /**
     * @param type    p. ej. {@code card.moved}
     * @param payload objeto serializable a JSON (records/mapas); es el campo {@code payload} del envelope
     */
    void publish(UUID boardId, String type, Object payload);
}
