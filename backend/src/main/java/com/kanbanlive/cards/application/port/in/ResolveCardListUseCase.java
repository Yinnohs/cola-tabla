package com.kanbanlive.cards.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Puerto de entrada que otras capacidades (p. ej. membership) usan para saber a que lista pertenece una tarjeta. */
public interface ResolveCardListUseCase {

    Optional<UUID> execute(UUID cardId);
}
