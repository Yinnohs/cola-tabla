package com.kanbanlive.cards.application.port.in;

import com.kanbanlive.sharedkernel.application.Command;
import java.util.UUID;

public interface CreateCardUseCase {

    /** La tarjeta se anade al final de la lista. */
    CardSummary execute(CreateCardCommand command);

    record CreateCardCommand(UUID listId, String title, String description) implements Command {
    }
}
