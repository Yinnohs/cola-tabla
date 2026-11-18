package com.kanbanlive.boards.application.port.in;

import com.kanbanlive.sharedkernel.application.Command;
import java.util.UUID;

public interface RenameBoardUseCase {

    BoardSummary execute(RenameBoardCommand command);

    /** {@code expectedVersion}: la version que el cliente leyo; si no coincide, STALE_VERSION. */
    record RenameBoardCommand(UUID boardId, String name, long expectedVersion) implements Command {
    }
}
