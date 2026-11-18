package com.kanbanlive.boards.application.port.in;

import com.kanbanlive.sharedkernel.application.Command;
import java.util.UUID;

public interface CreateBoardUseCase {

    BoardSummary execute(CreateBoardCommand command);

    record CreateBoardCommand(UUID workspaceId, String name) implements Command {
    }
}
