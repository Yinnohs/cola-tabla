package com.kanbanlive.boards.application.service;

import com.kanbanlive.boards.application.port.in.BoardSummary;
import com.kanbanlive.boards.domain.model.Board;

final class BoardSummaries {

    private BoardSummaries() {
    }

    static BoardSummary of(Board b) {
        return new BoardSummary(b.id(), b.workspaceId(), b.name(), b.version(), b.createdAt());
    }
}
