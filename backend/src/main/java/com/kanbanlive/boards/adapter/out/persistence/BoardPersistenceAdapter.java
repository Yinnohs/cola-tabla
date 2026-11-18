package com.kanbanlive.boards.adapter.out.persistence;

import com.kanbanlive.boards.application.port.out.BoardRepository;
import com.kanbanlive.boards.domain.model.Board;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class BoardPersistenceAdapter implements BoardRepository {

    private final BoardJpaRepository jpa;
    private final BoardEntityMapper mapper;

    BoardPersistenceAdapter(BoardJpaRepository jpa, BoardEntityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Board save(Board board) {
        try {
            BoardJpaEntity entity = jpa.findById(board.id()).map(existing -> {
                if (existing.getVersion() != null && existing.getVersion() != board.version()) {
                    throw new StaleVersionException("board " + board.id() + " was modified concurrently");
                }
                mapper.apply(board, existing);
                return existing;
            }).orElseGet(() -> mapper.toNewEntity(board));
            return mapper.toDomain(jpa.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleVersionException("board " + board.id() + " was modified concurrently");
        }
    }

    @Override
    public Optional<Board> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpa.existsById(id);
    }

    @Override
    public List<Board> findByWorkspaceId(UUID workspaceId) {
        return jpa.findByWorkspaceIdOrderByCreatedAtAscIdAsc(workspaceId).stream().map(mapper::toDomain).toList();
    }
}
