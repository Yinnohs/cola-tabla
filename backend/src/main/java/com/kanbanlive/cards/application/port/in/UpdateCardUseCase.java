package com.kanbanlive.cards.application.port.in;

import com.kanbanlive.sharedkernel.application.Command;
import java.util.UUID;

public interface UpdateCardUseCase {

    CardSummary execute(UpdateCardCommand command);

    /** Reemplaza titulo y descripcion; una expectedVersion obsoleta provoca STALE_VERSION. */
    record UpdateCardCommand(UUID cardId, String title, String description, long expectedVersion)
            implements Command {
    }
}
