package com.kanbanlive.lists.application.service;

import com.kanbanlive.lists.application.port.in.ListSummary;
import com.kanbanlive.lists.domain.model.BoardList;

final class ListSummaries {

    private ListSummaries() {
    }

    static ListSummary of(BoardList l) {
        return new ListSummary(l.id(), l.boardId(), l.name(), l.position().value(), l.version(), l.createdAt());
    }
}
