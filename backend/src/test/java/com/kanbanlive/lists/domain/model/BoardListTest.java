package com.kanbanlive.lists.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardListTest {

    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");
    static final UUID BOARD = UUID.randomUUID();
    static final FractionalIndex P1 = FractionalIndex.of("V");
    static final FractionalIndex P2 = FractionalIndex.of("k");

    @Test
    void createValidatesNameAndStartsAtVersionZero() {
        BoardList l = BoardList.create(UUID.randomUUID(), BOARD, " Todo ", P1, T0);
        assertThat(l.name()).isEqualTo("Todo");
        assertThat(l.version()).isZero();
        assertThat(l.position()).isEqualTo(P1);
        assertThatThrownBy(() -> BoardList.create(UUID.randomUUID(), BOARD, " ", P1, T0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void renameKeepsPositionVersionAndCreatedAt() {
        BoardList l = BoardList.rehydrate(UUID.randomUUID(), BOARD, "Old", P1, 4, T0, T0);
        BoardList renamed = l.rename("New", T1);
        assertThat(renamed.name()).isEqualTo("New");
        assertThat(renamed.position()).isEqualTo(P1);
        assertThat(renamed.version()).isEqualTo(4);
        assertThat(renamed.createdAt()).isEqualTo(T0);
        assertThat(renamed.updatedAt()).isEqualTo(T1);
        assertThat(l.name()).isEqualTo("Old");
    }

    @Test
    void renameRejectsBlank() {
        BoardList l = BoardList.create(UUID.randomUUID(), BOARD, "Old", P1, T0);
        assertThatThrownBy(() -> l.rename("", T1)).isInstanceOf(ValidationException.class);
    }

    @Test
    void moveToChangesOnlyPosition() {
        BoardList l = BoardList.rehydrate(UUID.randomUUID(), BOARD, "Todo", P1, 2, T0, T0);
        BoardList moved = l.moveTo(P2, T1);
        assertThat(moved.position()).isEqualTo(P2);
        assertThat(moved.name()).isEqualTo("Todo");
        assertThat(moved.boardId()).isEqualTo(BOARD);
        assertThat(moved.version()).isEqualTo(2);
        assertThat(l.position()).isEqualTo(P1);
    }
}
