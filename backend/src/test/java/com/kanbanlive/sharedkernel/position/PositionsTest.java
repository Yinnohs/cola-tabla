package com.kanbanlive.sharedkernel.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PositionsTest {

    @Test
    void allocatesBetweenNeighbours() {
        FractionalIndex a = FractionalIndex.of("A");
        FractionalIndex b = FractionalIndex.of("C");
        FractionalIndex mid = Positions.allocate(a, b);
        assertThat(mid).isGreaterThan(a).isLessThan(b);
    }

    @Test
    void allocatesIntoEmptyList() {
        assertThat(Positions.allocate(null, null)).isNotNull();
    }

    @Test
    void appendingForeverEventuallyRequiresRebalance() {
        FractionalIndex last = FractionalIndex.of("z");
        assertThatThrownBy(() -> {
            FractionalIndex current = last;
            for (int i = 0; i < 200; i++) {
                current = Positions.allocate(current, null);
            }
        }).isInstanceOf(RebalanceRequiredException.class)
                .extracting(e -> ((RebalanceRequiredException) e).code()).isEqualTo("REBALANCE_REQUIRED");
    }

    @Test
    void rebalanceAssignsIncreasingEvenlySpacedPositionsInTheGivenOrder() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        List<PositionAssignment> assignments = Positions.rebalance(ids);

        assertThat(assignments).extracting(PositionAssignment::id).containsExactlyElementsOf(ids);
        assertThat(assignments).extracting(PositionAssignment::position).isSorted().doesNotHaveDuplicates()
                .allMatch(p -> !p.isExhausted());
        assertThat(Positions.rebalance(List.of())).isEmpty();
    }
}
