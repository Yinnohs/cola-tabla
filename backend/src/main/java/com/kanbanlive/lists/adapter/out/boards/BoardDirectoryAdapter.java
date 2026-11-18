package com.kanbanlive.lists.adapter.out.boards;

import com.kanbanlive.boards.application.port.in.CheckBoardExistsUseCase;
import com.kanbanlive.lists.application.port.out.BoardDirectory;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Cruce entre capacidades permitido: lists a boards SOLO via su port.in. */
@Component
class BoardDirectoryAdapter implements BoardDirectory {

    private final CheckBoardExistsUseCase checkBoardExists;

    BoardDirectoryAdapter(CheckBoardExistsUseCase checkBoardExists) {
        this.checkBoardExists = checkBoardExists;
    }

    @Override
    public boolean exists(UUID boardId) {
        return checkBoardExists.execute(boardId);
    }
}
