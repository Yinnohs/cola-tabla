package com.kanbanlive.boards.adapter.in.web;

import com.kanbanlive.boards.application.port.in.BoardDetails;
import java.util.List;
import java.util.UUID;

record BoardDetailsResponse(UUID id, UUID workspaceId, String name, long version, List<BoardListResponse> lists) {

    record BoardListResponse(UUID id, String name, String position, long version, List<BoardCardResponse> cards) {
    }

    record BoardCardResponse(UUID id, String title, String description, String position, long version) {
    }

    static BoardDetailsResponse from(BoardDetails d) {
        return new BoardDetailsResponse(d.id(), d.workspaceId(), d.name(), d.version(),
                d.lists().stream()
                        .map(l -> new BoardListResponse(l.id(), l.name(), l.position(), l.version(),
                                l.cards().stream()
                                        .map(c -> new BoardCardResponse(c.id(), c.title(), c.description(), c.position(),
                                                c.version()))
                                        .toList()))
                        .toList());
    }
}
