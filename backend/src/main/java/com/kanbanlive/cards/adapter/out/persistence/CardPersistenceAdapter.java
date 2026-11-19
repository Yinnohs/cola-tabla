package com.kanbanlive.cards.adapter.out.persistence;

import com.kanbanlive.cards.application.port.out.CardRepository;
import com.kanbanlive.cards.domain.model.Card;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
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
class CardPersistenceAdapter implements CardRepository {

    private final CardJpaRepository jpa;
    private final CardEntityMapper mapper;

    CardPersistenceAdapter(CardJpaRepository jpa, CardEntityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Card save(Card card) {
        try {
            CardJpaEntity entity = jpa.findById(card.id()).map(existing -> {
                if (existing.getVersion() != null && existing.getVersion() != card.version()) {
                    throw new StaleVersionException("card " + card.id() + " was modified concurrently");
                }
                mapper.apply(card, existing);
                return existing;
            }).orElseGet(() -> mapper.toNewEntity(card));
            return mapper.toDomain(jpa.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleVersionException("card " + card.id() + " was modified concurrently");
        }
    }

    @Override
    public Card saveMove(Card card) {
        if (jpa.move(card.id(), card.listId(), card.position().value(), card.updatedAt()) == 0) {
            throw new NotFoundException("card " + card.id() + " not found");
        }
        return jpa.findById(card.id()).map(mapper::toDomain)
                .orElseThrow(() -> new NotFoundException("card " + card.id() + " not found"));
    }

    @Override
    public Optional<Card> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        try {
            jpa.deleteById(id);
            jpa.flush();
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleVersionException("card " + id + " was modified concurrently");
        }
    }

    @Override
    public Optional<FractionalIndex> findLastPosition(UUID listId) {
        return jpa.findLastPosition(listId).map(FractionalIndex::of);
    }

    @Override
    public Optional<FractionalIndex> findPositionInList(UUID listId, UUID cardId) {
        return jpa.findPositionInList(listId, cardId).map(FractionalIndex::of);
    }

    @Override
    public List<UUID> findIdsInOrder(UUID listId) {
        return jpa.findIdsInOrder(listId);
    }

    @Override
    public void updatePositions(List<PositionAssignment> assignments, Instant now) {
        Map<UUID, CardJpaEntity> entities = jpa.findAllById(assignments.stream().map(PositionAssignment::id).toList())
                .stream().collect(Collectors.toMap(CardJpaEntity::getId, Function.identity()));
        for (PositionAssignment a : assignments) {
            CardJpaEntity entity = entities.get(a.id());
            entity.setPosition(a.position().value());
            entity.setUpdatedAt(now);
        }
        jpa.saveAll(entities.values());
        jpa.flush();
    }
}
