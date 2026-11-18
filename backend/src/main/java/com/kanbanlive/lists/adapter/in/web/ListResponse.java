package com.kanbanlive.lists.adapter.in.web;

import com.kanbanlive.lists.application.port.in.ListSummary;
import java.time.Instant;
import java.util.UUID;

record ListResponse(UUID id, UUID boardId, String name, String position, long version, Instant createdAt) {

    static ListResponse from(ListSummary s) {
        return new ListResponse(s.id(), s.boardId(), s.name(), s.position(), s.version(), s.createdAt());
    }
}
