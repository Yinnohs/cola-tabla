package com.kanbanlive.platform.security;

import com.kanbanlive.membership.application.port.in.CheckAccessUseCase;
import com.kanbanlive.membership.application.port.in.CheckAccessUseCase.Permission;
import com.kanbanlive.sharedkernel.application.CurrentUser;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bean "perms" para SpEL: {@code @PreAuthorize("@perms.canEdit(#boardId)")}. El id puede ser un workspaceId o un
 * boardId (se resuelve el workspace). Sin membresia o id desconocido => false. Con
 * kanban.security.enabled=false (solo local) permite todo.
 */
@Component("perms")
public class Perms {

    private final CheckAccessUseCase access;
    private final CurrentUser currentUser;
    private final boolean securityEnabled;

    Perms(CheckAccessUseCase access, CurrentUser currentUser,
            @Value("${kanban.security.enabled:true}") boolean securityEnabled) {
        this.access = access;
        this.currentUser = currentUser;
        this.securityEnabled = securityEnabled;
    }

    public boolean canView(UUID id) {
        return check(id, Permission.VIEW);
    }

    public boolean canEdit(UUID id) {
        return check(id, Permission.EDIT);
    }

    public boolean canAdmin(UUID id) {
        return check(id, Permission.ADMIN);
    }

    private boolean check(UUID id, Permission permission) {
        if (!securityEnabled) {
            return true;
        }
        return access.execute(currentUser.id(), id, permission);
    }
}
