package com.kanbanlive.collaboration.adapter.out.stomp;

import com.kanbanlive.collaboration.application.port.out.BoardEventBroadcaster;
import com.kanbanlive.collaboration.domain.model.BoardEnvelope;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Fase 3: broker STOMP en memoria de esta instancia. En Fase 6 lo sustituye/complementa Redis pub/sub. */
@Component
class LocalStompBroadcaster implements BoardEventBroadcaster {

    private final SimpMessagingTemplate template;

    LocalStompBroadcaster(SimpMessagingTemplate template) {
        this.template = template;
    }

    @Override
    public void broadcast(BoardEnvelope envelope) {
        template.convertAndSend("/topic/boards." + envelope.boardId(), envelope);
    }
}
