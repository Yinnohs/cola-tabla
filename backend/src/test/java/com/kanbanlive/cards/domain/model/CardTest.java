package com.kanbanlive.cards.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CardTest {

    static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    static final Instant T1 = Instant.parse("2026-01-02T00:00:00Z");
    static final UUID LIST = UUID.randomUUID();
    static final FractionalIndex P1 = FractionalIndex.of("V");
    static final FractionalIndex P2 = FractionalIndex.of("k");

    @Test
    void createValidatesTitleAndAllowsNullDescription() {
        Card c = Card.create(UUID.randomUUID(), LIST, " Fix bug ", null, P1, T0);
        assertThat(c.title()).isEqualTo("Fix bug");
        assertThat(c.description()).isNull();
        assertThat(c.version()).isZero();
        assertThatThrownBy(() -> Card.create(UUID.randomUUID(), LIST, "", null, P1, T0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createRejectsTooLongDescription() {
        String tooLong = "x".repeat(Card.DESCRIPTION_MAX + 1);
        assertThatThrownBy(() -> Card.create(UUID.randomUUID(), LIST, "t", tooLong, P1, T0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateReplacesTitleAndDescriptionKeepingPositionAndVersion() {
        Card c = Card.rehydrate(UUID.randomUUID(), LIST, "Old", "d", P1, 3, T0, T0);
        Card updated = c.update("New", null, T1);
        assertThat(updated.title()).isEqualTo("New");
        assertThat(updated.description()).isNull();
        assertThat(updated.position()).isEqualTo(P1);
        assertThat(updated.listId()).isEqualTo(LIST);
        assertThat(updated.version()).isEqualTo(3);
        assertThat(updated.updatedAt()).isEqualTo(T1);
        assertThat(c.title()).isEqualTo("Old");
    }

    @Test
    void updateRejectsBlankTitle() {
        Card c = Card.create(UUID.randomUUID(), LIST, "Old", null, P1, T0);
        assertThatThrownBy(() -> c.update(" ", "d", T1)).isInstanceOf(ValidationException.class);
    }

    @Test
    void moveToChangesListAndPositionOnly() {
        UUID other = UUID.randomUUID();
        Card c = Card.rehydrate(UUID.randomUUID(), LIST, "T", "d", P1, 2, T0, T0);
        Card moved = c.moveTo(other, P2, T1);
        assertThat(moved.listId()).isEqualTo(other);
        assertThat(moved.position()).isEqualTo(P2);
        assertThat(moved.title()).isEqualTo("T");
        assertThat(moved.description()).isEqualTo("d");
        assertThat(moved.version()).isEqualTo(2);
        assertThat(c.listId()).isEqualTo(LIST);
    }
}
