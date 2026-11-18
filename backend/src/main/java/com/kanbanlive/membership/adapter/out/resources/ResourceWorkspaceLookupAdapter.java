package com.kanbanlive.membership.adapter.out.resources;

import com.kanbanlive.boards.application.port.in.ResolveBoardWorkspaceUseCase;
import com.kanbanlive.cards.application.port.in.ResolveCardListUseCase;
import com.kanbanlive.lists.application.port.in.ResolveListBoardUseCase;
import com.kanbanlive.membership.application.port.out.ResourceWorkspaceLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Cruce entre capacidades permitido: membership -> boards/lists/cards SOLO via sus port.in. */
@Component
class ResourceWorkspaceLookupAdapter implements ResourceWorkspaceLookup {

    private final ResolveBoardWorkspaceUseCase boards;
    private final ResolveListBoardUseCase lists;
    private final ResolveCardListUseCase cards;

    ResourceWorkspaceLookupAdapter(ResolveBoardWorkspaceUseCase boards, ResolveListBoardUseCase lists,
            ResolveCardListUseCase cards) {
        this.boards = boards;
        this.lists = lists;
        this.cards = cards;
    }

    @Override
    public Optional<UUID> workspaceOf(UUID id) {
        return boards.execute(id)
                .or(() -> lists.execute(id).flatMap(boards::execute))
                .or(() -> cards.execute(id).flatMap(lists::execute).flatMap(boards::execute));
    }
}
