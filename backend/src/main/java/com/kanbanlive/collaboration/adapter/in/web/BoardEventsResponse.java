package com.kanbanlive.collaboration.adapter.in.web;

import com.kanbanlive.collaboration.application.port.in.GetBoardEventsSinceUseCase.BoardEventsSince;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** {@code lastSeq}: seq actual del tablero; el cliente continua desde ahi. Cada evento tiene la forma del WS. */
record BoardEventsResponse(UUID boardId, long since, long lastSeq, List<Event> events) {

    record Event(UUID boardId, long seq, String type, Object payload, String clientId, Instant occurredAt,
            int schemaVersion) {
    }

    static BoardEventsResponse from(BoardEventsSince r) {
        return new BoardEventsResponse(r.boardId(), r.since(), r.lastSeq(), r.events().stream()
                .map(e -> new Event(e.boardId(), e.seq(), e.type(), e.payload(), e.clientId(), e.occurredAt(),
                        e.schemaVersion()))
                .toList());
    }
}
