package com.kanbanlive.boards.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.boards.application.port.in.BoardEventRecord;
import com.kanbanlive.boards.application.port.in.PurgeBoardEventsUseCase.PurgeBoardEventsCommand;
import com.kanbanlive.boards.application.port.out.BoardEventLog;
import com.kanbanlive.testsupport.FixedClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** B4.3: la purga usa el cutoff ahora - retencion y trabaja en lotes acotados. */
class PurgeBoardEventsServiceTest {

    static final class FakeLog implements BoardEventLog {
        final List<Instant> rows = new ArrayList<>();
        final List<Integer> batchSizes = new ArrayList<>();
        Instant lastCutoff;

        @Override
        public long append(UUID boardId, String type, String payloadJson) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Long> currentSeq(UUID boardId) {
            return Optional.empty();
        }

        @Override
        public List<BoardEventRecord> findAfter(UUID boardId, long since, long upTo, int limit) {
            return List.of();
        }

        @Override
        public int deleteOlderThan(Instant cutoff, int batchSize) {
            lastCutoff = cutoff;
            batchSizes.add(batchSize);
            int deleted = 0;
            for (var it = rows.iterator(); it.hasNext() && deleted < batchSize;) {
                if (it.next().isBefore(cutoff)) {
                    it.remove();
                    deleted++;
                }
            }
            return deleted;
        }
    }

    final FakeLog log = new FakeLog();
    final FixedClock clock = new FixedClock(Instant.parse("2026-06-02T12:00:00Z"));
    final PurgeBoardEventsService service = new PurgeBoardEventsService(log, clock);

    @Test
    void deletesOnlyEventsOlderThanTheRetentionWindow() {
        Instant now = clock.now();
        log.rows.addAll(List.of(now.minus(Duration.ofHours(30)), now.minus(Duration.ofHours(25)),
                now.minus(Duration.ofHours(23)), now));

        int deleted = service.execute(new PurgeBoardEventsCommand(Duration.ofHours(24), 100, 10));

        assertThat(deleted).isEqualTo(2);
        assertThat(log.rows).hasSize(2);
        assertThat(log.lastCutoff).isEqualTo(now.minus(Duration.ofHours(24)));
    }

    @Test
    void stopsAtMaxBatchesLeavingTheRestForTheNextRun() {
        for (int i = 0; i < 10; i++) {
            log.rows.add(clock.now().minus(Duration.ofDays(2)));
        }

        int deleted = service.execute(new PurgeBoardEventsCommand(Duration.ofHours(24), 3, 2));

        assertThat(deleted).isEqualTo(6);
        assertThat(log.rows).hasSize(4);
        assertThat(log.batchSizes).containsExactly(3, 3);
    }

    @Test
    void stopsAsSoonAsABatchComesBackShort() {
        log.rows.add(clock.now().minus(Duration.ofDays(2)));

        service.execute(new PurgeBoardEventsCommand(Duration.ofHours(24), 5, 10));

        assertThat(log.batchSizes).containsExactly(5);
    }

    @Test
    void rejectsNonPositiveBoundsOrNegativeRetention() {
        assertThatThrownBy(() -> service.execute(new PurgeBoardEventsCommand(Duration.ofHours(1), 0, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.execute(new PurgeBoardEventsCommand(Duration.ofHours(1), 1, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.execute(new PurgeBoardEventsCommand(Duration.ofHours(-1), 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
