package com.kanbanlive.lists.adapter.out.persistence;

import com.kanbanlive.lists.domain.model.BoardList;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import org.springframework.stereotype.Component;

@Component
class ListEntityMapper {

    BoardList toDomain(ListJpaEntity e) {
        long version = e.getVersion() == null ? 0 : e.getVersion();
        return BoardList.rehydrate(e.getId(), e.getBoardId(), e.getName(), FractionalIndex.of(e.getPosition()),
                version, e.getCreatedAt(), e.getUpdatedAt());
    }

    ListJpaEntity toNewEntity(BoardList l) {
        return new ListJpaEntity(l.id(), l.boardId(), l.name(), l.position().value(), l.createdAt(), l.updatedAt());
    }

    void apply(BoardList l, ListJpaEntity e) {
        e.setName(l.name());
        e.setPosition(l.position().value());
        e.setUpdatedAt(l.updatedAt());
    }
}
