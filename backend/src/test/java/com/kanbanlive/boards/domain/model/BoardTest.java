package com.kanbanlive.boards.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.sharedkernel.domain.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardTest {

    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");
    static final UUID WS = UUID.randomUUID();

    @Test
    void createValidatesName() {
        assertThat(Board.create(UUID.randomUUID(), WS, " Sprint ", T0).name()).isEqualTo("Sprint");
        assertThatThrownBy(() -> Board.create(UUID.randomUUID(), WS, "", T0)).isInstanceOf(ValidationException.class);
    }

    @Test
    void renameKeepsIdentityVersionAndCreatedAt() {
        Board b = Board.rehydrate(UUID.randomUUID(), WS, "Old", 3, T0, T0);
        Board renamed = b.rename("New", T1);
        assertThat(renamed.name()).isEqualTo("New");
        assertThat(renamed.id()).isEqualTo(b.id());
        assertThat(renamed.version()).isEqualTo(3);
        assertThat(renamed.createdAt()).isEqualTo(T0);
        assertThat(renamed.updatedAt()).isEqualTo(T1);
        assertThat(b.name()).isEqualTo("Old");
    }

    @Test
    void renameRejectsBlank() {
        Board b = Board.create(UUID.randomUUID(), WS, "Old", T0);
        assertThatThrownBy(() -> b.rename("  ", T1)).isInstanceOf(ValidationException.class);
    }
}
