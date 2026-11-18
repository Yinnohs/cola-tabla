package com.kanbanlive.cards.application.port.in;

import com.kanbanlive.sharedkernel.application.Command;
import java.util.UUID;

public interface DeleteCardUseCase {

    /** Borrado fisico (hard delete): no hay dependientes ni papelera en Fase 1. */
    void execute(DeleteCardCommand command);

    record DeleteCardCommand(UUID cardId) implements Command {
    }
}
