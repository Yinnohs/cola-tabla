package com.kanbanlive.boards.adapter.out.persistence;

import com.kanbanlive.boards.domain.model.Board;
import org.springframework.stereotype.Component;

@Component
class BoardEntityMapper {

    Board toDomain(BoardJpaEntity e) {
        long version = e.getVersion() == null ? 0 : e.getVersion();
        return Board.rehydrate(e.getId(), e.getWorkspaceId(), e.getName(), version, e.getCreatedAt(),
                e.getUpdatedAt());
    }

    BoardJpaEntity toNewEntity(Board b) {
        return new BoardJpaEntity(b.id(), b.workspaceId(), b.name(), b.createdAt(), b.updatedAt());
    }

    void apply(Board b, BoardJpaEntity e) {
        e.setName(b.name());
        e.setUpdatedAt(b.updatedAt());
    }
}
