package com.kanbanlive.collaboration.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanbanlive.boards.application.port.in.BoardEventRecord;
import com.kanbanlive.boards.application.port.in.ReadBoardEventLogUseCase;
import com.kanbanlive.collaboration.application.port.in.GetBoardEventsSinceUseCase;
import com.kanbanlive.collaboration.domain.error.EventsGoneException;
import com.kanbanlive.collaboration.domain.error.InvalidEventCursorException;
import com.kanbanlive.collaboration.domain.model.BoardEnvelope;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Politica de resync (ADR-0005): el log es contiguo (seq sin huecos, ADR-0008), asi que los eventos pedidos son
 * exactamente {@code lastSeq - since}. Si faltan mas de {@code max-gap}, o el log retenido ya no los contiene
 * completos (purga por retencion), el cliente debe recargar el tablero entero: 410.
 */
@Service
@Transactional(readOnly = true)
class GetBoardEventsSinceService implements GetBoardEventsSinceUseCase {

    private final ReadBoardEventLogUseCase log;
    private final ObjectMapper mapper;
    private final int maxGap;

    GetBoardEventsSinceService(ReadBoardEventLogUseCase log, ObjectMapper mapper,
            @Value("${kanban.events.max-gap:500}") int maxGap) {
        this.log = log;
        this.mapper = mapper;
        this.maxGap = maxGap;
    }

    @Override
    public BoardEventsSince execute(UUID boardId, long since) {
        long current = log.currentSeq(boardId);
        if (since < 0) {
            throw new InvalidEventCursorException("since must be >= 0");
        }
        if (since > current) {
            throw new InvalidEventCursorException(
                    "since " + since + " is ahead of the board's current seq " + current);
        }
        if (since == current) {
            return new BoardEventsSince(boardId, since, current, List.of());
        }
        long missing = current - since;
        if (missing > maxGap) {
            throw new EventsGoneException(missing + " events missing (max " + maxGap + "): reload the board");
        }
        List<BoardEventRecord> records = log.eventsAfter(boardId, since, current, maxGap);
        if (records.size() != missing || records.get(0).seq() != since + 1) {
            // Parte del tramo pedido ya fue purgado por retencion: no se puede reconstruir el estado desde el log.
            throw new EventsGoneException("events after seq " + since + " are no longer retained: reload the board");
        }
        return new BoardEventsSince(boardId, since, current, records.stream().map(this::toEnvelope).toList());
    }

    private BoardEnvelope toEnvelope(BoardEventRecord r) {
        try {
            // clientId no se persiste en el log: un evento reproducido nunca se reconoce como eco propio.
            return new BoardEnvelope(r.boardId(), r.seq(), r.type(), mapper.readTree(r.payloadJson()), null,
                    r.createdAt(), BoardEnvelope.SCHEMA_VERSION);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("corrupt payload for board " + r.boardId() + " seq " + r.seq(), e);
        }
    }
}
