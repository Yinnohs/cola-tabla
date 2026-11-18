package com.kanbanlive.membership.application.port.in;

import java.util.UUID;

/**
 * Consulta de permisos (la usa el bean perms). Un recurso inexistente o ajeno => false.
 * resourceId puede ser un workspaceId, boardId, listId o cardId.
 */
public interface CheckAccessUseCase {

    boolean execute(String userId, UUID resourceId, Permission permission);

    enum Permission { VIEW, EDIT, ADMIN }
}
