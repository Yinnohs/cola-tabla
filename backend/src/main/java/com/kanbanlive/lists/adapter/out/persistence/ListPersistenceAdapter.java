package com.kanbanlive.lists.adapter.out.persistence;

import com.kanbanlive.lists.application.port.out.ListRepository;
import com.kanbanlive.lists.domain.model.BoardList;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import com.kanbanlive.sharedkernel.position.PositionAssignment;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class ListPersistenceAdapter implements ListRepository {

    private final ListJpaRepository jpa;
    private final ListEntityMapper mapper;

    ListPersistenceAdapter(ListJpaRepository jpa, ListEntityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public BoardList save(BoardList list) {
        try {
            ListJpaEntity entity = jpa.findById(list.id()).map(existing -> {
                if (existing.getVersion() != null && existing.getVersion() != list.version()) {
                    throw new StaleVersionException("list " + list.id() + " was modified concurrently");
                }
                mapper.apply(list, existing);
                return existing;
            }).orElseGet(() -> mapper.toNewEntity(list));
            return mapper.toDomain(jpa.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleVersionException("list " + list.id() + " was modified concurrently");
        }
    }

    @Override
    public Optional<BoardList> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpa.existsById(id);
    }

    @Override
    public Optional<FractionalIndex> findLastPosition(UUID boardId) {
        return jpa.findLastPosition(boardId).map(FractionalIndex::of);
    }

    @Override
    public Optional<FractionalIndex> findPositionInBoard(UUID boardId, UUID listId) {
        return jpa.findPositionInBoard(boardId, listId).map(FractionalIndex::of);
    }

    @Override
    public List<UUID> findIdsInOrder(UUID boardId) {
        return jpa.findIdsInOrder(boardId);
    }

    @Override
    public void updatePositions(List<PositionAssignment> assignments, Instant now) {
        Map<UUID, ListJpaEntity> entities = jpa.findAllById(assignments.stream().map(PositionAssignment::id).toList())
                .stream().collect(Collectors.toMap(ListJpaEntity::getId, Function.identity()));
        for (PositionAssignment a : assignments) {
            ListJpaEntity entity = entities.get(a.id());
            entity.setPosition(a.position().value());
            entity.setUpdatedAt(now);
        }
        jpa.saveAll(entities.values());
        jpa.flush();
    }
}
