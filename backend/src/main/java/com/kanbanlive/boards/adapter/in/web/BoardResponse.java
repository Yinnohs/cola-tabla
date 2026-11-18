package com.kanbanlive.boards.adapter.in.web;

import com.kanbanlive.boards.application.port.in.BoardSummary;
import java.time.Instant;
import java.util.UUID;

record BoardResponse(UUID id, UUID workspaceId, String name, long version, Instant createdAt) {

    static BoardResponse from(BoardSummary s) {
        return new BoardResponse(s.id(), s.workspaceId(), s.name(), s.version(), s.createdAt());
    }
}
