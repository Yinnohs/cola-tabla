package com.kanbanlive.cards.adapter.out.persistence;

import com.kanbanlive.cards.domain.model.Card;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import org.springframework.stereotype.Component;

@Component
class CardEntityMapper {

    Card toDomain(CardJpaEntity e) {
        long version = e.getVersion() == null ? 0 : e.getVersion();
        return Card.rehydrate(e.getId(), e.getListId(), e.getTitle(), e.getDescription(),
                FractionalIndex.of(e.getPosition()), version, e.getCreatedAt(), e.getUpdatedAt());
    }

    CardJpaEntity toNewEntity(Card c) {
        return new CardJpaEntity(c.id(), c.listId(), c.title(), c.description(), c.position().value(),
                c.createdAt(), c.updatedAt());
    }

    void apply(Card c, CardJpaEntity e) {
        e.setListId(c.listId());
        e.setTitle(c.title());
        e.setDescription(c.description());
        e.setPosition(c.position().value());
        e.setUpdatedAt(c.updatedAt());
    }
}
