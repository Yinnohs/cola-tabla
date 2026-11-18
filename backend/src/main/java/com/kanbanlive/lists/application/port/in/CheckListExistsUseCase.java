package com.kanbanlive.lists.application.port.in;

import java.util.UUID;

/** Puerto de entrada que otras capacidades (p. ej. cards) usan para validar la existencia de una lista. */
public interface CheckListExistsUseCase {

    boolean execute(UUID listId);
}
