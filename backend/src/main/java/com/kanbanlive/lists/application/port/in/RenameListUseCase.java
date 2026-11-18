package com.kanbanlive.lists.application.port.in;

import com.kanbanlive.sharedkernel.application.Command;
import java.util.UUID;

public interface RenameListUseCase {

    ListSummary execute(RenameListCommand command);

    /** {@code expectedVersion}: la version que el cliente leyo; si no coincide, STALE_VERSION. */
    record RenameListCommand(UUID listId, String name, long expectedVersion) implements Command {
    }
}
