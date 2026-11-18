package com.kanbanlive.boards.application.port.in;

import java.util.UUID;

public interface GetBoardUseCase {

    BoardDetails execute(UUID boardId);
}
