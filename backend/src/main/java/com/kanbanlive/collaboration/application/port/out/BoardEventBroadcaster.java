package com.kanbanlive.collaboration.application.port.out;

import com.kanbanlive.collaboration.domain.model.BoardEnvelope;

/** Difunde un envelope a los suscriptores del tablero. Fase 3: STOMP local; Fase 6: Redis pub/sub. */
public interface BoardEventBroadcaster {

    void broadcast(BoardEnvelope envelope);
}
