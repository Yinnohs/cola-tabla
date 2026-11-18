package com.kanbanlive.collaboration.application.service;

import com.kanbanlive.collaboration.application.port.in.BroadcastBoardEventUseCase;
import com.kanbanlive.collaboration.application.port.out.BoardEventBroadcaster;
import com.kanbanlive.collaboration.domain.model.BoardEnvelope;
import org.springframework.stereotype.Service;

@Service
class BroadcastBoardEventService implements BroadcastBoardEventUseCase {

    private final BoardEventBroadcaster broadcaster;

    BroadcastBoardEventService(BoardEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void execute(BoardEnvelope envelope) {
        broadcaster.broadcast(envelope);
    }
}
