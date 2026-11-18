package com.kanbanlive.workspaces.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.sharedkernel.domain.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceTest {

    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createTrimsNameAndStartsAtVersionZero() {
        Workspace w = Workspace.create(UUID.randomUUID(), "  Team  ", NOW);
        assertThat(w.name()).isEqualTo("Team");
        assertThat(w.version()).isZero();
        assertThat(w.createdAt()).isEqualTo(NOW);
        assertThat(w.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void createRejectsBlankOrTooLongName() {
        assertThatThrownBy(() -> Workspace.create(UUID.randomUUID(), " ", NOW)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Workspace.create(UUID.randomUUID(), "x".repeat(121), NOW))
                .isInstanceOf(ValidationException.class);
    }
}
