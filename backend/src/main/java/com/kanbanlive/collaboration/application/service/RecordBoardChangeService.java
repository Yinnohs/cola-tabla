package com.kanbanlive.collaboration.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanbanlive.boards.application.port.in.RecordBoardEventUseCase;
import com.kanbanlive.collaboration.domain.event.BoardEventRecorded;
import com.kanbanlive.collaboration.domain.model.BoardEnvelope;
import com.kanbanlive.sharedkernel.application.BoardChangePublisher;
import com.kanbanlive.sharedkernel.application.ClientContext;
import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.application.DomainEventPublisher;
import com.kanbanlive.sharedkernel.application.IdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dentro de la tx del caso de uso: asigna seq y guarda board_event (boards) y publica {@link BoardEventRecorded}.
 * La difusion ocurre solo en AFTER_COMMIT (listener en adapter.in.event): un rollback no emite nada.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
class RecordBoardChangeService implements BoardChangePublisher {

    private final RecordBoardEventUseCase recordEvent;
    private final DomainEventPublisher events;
    private final ClientContext client;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final IdGenerator ids;

    RecordBoardChangeService(RecordBoardEventUseCase recordEvent, DomainEventPublisher events, ClientContext client,
            ObjectMapper mapper, Clock clock, IdGenerator ids) {
        this.recordEvent = recordEvent;
        this.events = events;
        this.client = client;
        this.mapper = mapper;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public void publish(UUID boardId, String type, Object payload) {
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize payload of " + type, e);
        }
        long seq = recordEvent.execute(boardId, type, json);
        Instant now = clock.now();
        BoardEnvelope envelope = new BoardEnvelope(boardId, seq, type, payload, client.clientId().orElse(null), now,
                BoardEnvelope.SCHEMA_VERSION);
        events.publish(List.of(new BoardEventRecorded(ids.newId(), now, envelope)));
    }
}
