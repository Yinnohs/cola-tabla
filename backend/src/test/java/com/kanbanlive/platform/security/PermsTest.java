package com.kanbanlive.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.kanbanlive.membership.application.port.in.CheckAccessUseCase;
import com.kanbanlive.membership.application.port.in.CheckAccessUseCase.Permission;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermsTest {

    final UUID id = UUID.randomUUID();
    // Fake: u1 puede VIEW y EDIT sobre id, nada mas.
    final CheckAccessUseCase access = (user, resource, permission) ->
            user.equals("u1") && resource.equals(id) && permission != Permission.ADMIN;

    @Test
    void delegatesToMembershipPort() {
        Perms perms = new Perms(access, () -> "u1", true);
        assertThat(perms.canView(id)).isTrue();
        assertThat(perms.canEdit(id)).isTrue();
        assertThat(perms.canAdmin(id)).isFalse();
        assertThat(perms.canView(UUID.randomUUID())).isFalse();
    }

    @Test
    void strangerIsDenied() {
        assertThat(new Perms(access, () -> "u2", true).canView(id)).isFalse();
    }

    @Test
    void permitsEverythingWhenSecurityDisabled() {
        Perms perms = new Perms(access, () -> "u2", false);
        assertThat(perms.canAdmin(id)).isTrue();
        assertThat(perms.canEdit(UUID.randomUUID())).isTrue();
    }
}
