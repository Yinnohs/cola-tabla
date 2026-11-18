package com.kanbanlive.collaboration.application.port.in;

import com.kanbanlive.collaboration.domain.model.BoardEnvelope;

/** Difusion de un evento ya confirmado (lo invoca el listener AFTER_COMMIT). */
public interface BroadcastBoardEventUseCase {

    void execute(BoardEnvelope envelope);
}
