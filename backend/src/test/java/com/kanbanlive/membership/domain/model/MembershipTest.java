package com.kanbanlive.membership.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.sharedkernel.domain.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipTest {

    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void rolePermissionsAreCumulative() {
        assertThat(Role.VIEWER.canView()).isTrue();
        assertThat(Role.VIEWER.canEdit()).isFalse();
        assertThat(Role.VIEWER.canAdmin()).isFalse();
        assertThat(Role.EDITOR.canEdit()).isTrue();
        assertThat(Role.EDITOR.canAdmin()).isFalse();
        assertThat(Role.OWNER.canAdmin()).isTrue();
    }

    @Test
    void createValidatesUserAndChangesRole() {
        Membership m = Membership.create(UUID.randomUUID(), UUID.randomUUID(), " u1 ", Role.VIEWER, NOW);
        assertThat(m.userId()).isEqualTo("u1");
        m.changeRole(Role.EDITOR, NOW.plusSeconds(1));
        assertThat(m.role()).isEqualTo(Role.EDITOR);
        assertThat(m.updatedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThatThrownBy(() -> Membership.create(UUID.randomUUID(), UUID.randomUUID(), " ", Role.VIEWER, NOW))
                .isInstanceOf(ValidationException.class);
    }
}
