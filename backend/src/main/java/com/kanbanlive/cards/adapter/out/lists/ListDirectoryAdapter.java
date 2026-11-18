package com.kanbanlive.cards.adapter.out.lists;

import com.kanbanlive.cards.application.port.out.ListDirectory;
import com.kanbanlive.lists.application.port.in.CheckListExistsUseCase;
import com.kanbanlive.lists.application.port.in.ResolveListBoardUseCase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Cruce entre capacidades permitido: cards a lists SOLO via su port.in. */
@Component
class ListDirectoryAdapter implements ListDirectory {

    private final CheckListExistsUseCase checkListExists;
    private final ResolveListBoardUseCase resolveBoard;

    ListDirectoryAdapter(CheckListExistsUseCase checkListExists, ResolveListBoardUseCase resolveBoard) {
        this.checkListExists = checkListExists;
        this.resolveBoard = resolveBoard;
    }

    @Override
    public boolean exists(UUID listId) {
        return checkListExists.execute(listId);
    }

    @Override
    public Optional<UUID> boardIdOf(UUID listId) {
        return resolveBoard.execute(listId);
    }
}
