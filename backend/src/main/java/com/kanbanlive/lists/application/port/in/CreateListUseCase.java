package com.kanbanlive.lists.application.port.in;

import com.kanbanlive.sharedkernel.application.Command;
import java.util.UUID;

public interface CreateListUseCase {

    /** La lista se anade al final del tablero. */
    ListSummary execute(CreateListCommand command);

    record CreateListCommand(UUID boardId, String name) implements Command {
    }
}
