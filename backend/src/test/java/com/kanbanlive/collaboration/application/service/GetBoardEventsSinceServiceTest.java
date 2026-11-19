package com.kanbanlive.collaboration.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanbanlive.boards.application.port.in.BoardEventRecord;
import com.kanbanlive.boards.application.port.in.ReadBoardEventLogUseCase;
import com.kanbanlive.collaboration.application.port.in.GetBoardEventsSinceUseCase.BoardEventsSince;
import com.kanbanlive.collaboration.domain.error.EventsGoneException;
import com.kanbanlive.collaboration.domain.error.InvalidEventCursorException;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** B4.1/B4.2 con un log en memoria: since valido, vacio, 400, 410 por max-gap y por purga. */
class GetBoardEventsSinceServiceTest {

    static final class FakeLog implements ReadBoardEventLogUseCase {
        final UUID board;
        long current;
        final List<BoardEventRecord> retained = new ArrayList<>();

        FakeLog(UUID board) {
            this.board = board;
        }

        void append(int count) {
            for (int i = 0; i < count; i++) {
                current++;
                retained.add(new BoardEventRecord(board, current, "card.created", "{\"n\":" + current + "}",
                        Instant.parse("2026-01-01T00:00:00Z")));
            }
        }

        @Override
        public long currentSeq(UUID boardId) {
            if (!boardId.equals(board)) {
                throw new NotFoundException("board not found");
            }
            return current;
        }

        @Override
        public List<BoardEventRecord> eventsAfter(UUID boardId, long since, long upTo, int limit) {
            return retained.stream().filter(e -> e.seq() > since && e.seq() <= upTo).limit(limit).toList();
        }
    }

    final UUID board = UUID.randomUUID();
    final FakeLog log = new FakeLog(board);
    final GetBoardEventsSinceService service = new GetBoardEventsSinceService(log, new ObjectMapper(), 5);

    @Test
    void returnsEventsAfterSinceInOrderWithLastSeq() {
        log.append(4);

        BoardEventsSince result = service.execute(board, 1);

        assertThat(result.lastSeq()).isEqualTo(4);
        assertThat(result.events()).extracting(e -> e.seq()).containsExactly(2L, 3L, 4L);
        assertThat(result.events().get(0).boardId()).isEqualTo(board);
        assertThat(result.events().get(0).type()).isEqualTo("card.created");
        assertThat(result.events().get(0).clientId()).isNull();
        assertThat(((JsonNode) result.events().get(0).payload()).get("n").asInt()).isEqualTo(2);
    }

    @Test
    void sinceEqualToCurrentIsAnEmptyList() {
        log.append(3);

        BoardEventsSince result = service.execute(board, 3);

        assertThat(result.events()).isEmpty();
        assertThat(result.lastSeq()).isEqualTo(3);
    }

    @Test
    void boardWithoutEventsAndSinceZeroIsEmpty() {
        assertThat(service.execute(board, 0).events()).isEmpty();
    }

    @Test
    void sinceAheadOfCurrentOrNegativeIsInvalid() {
        log.append(3);
        assertThatThrownBy(() -> service.execute(board, 4)).isInstanceOf(InvalidEventCursorException.class);
        assertThatThrownBy(() -> service.execute(board, -1)).isInstanceOf(InvalidEventCursorException.class);
    }

    @Test
    void exactlyMaxGapMissingIsServedButOneMoreIsGone() {
        log.append(6);
        assertThat(service.execute(board, 1).events()).hasSize(5);
        assertThatThrownBy(() -> service.execute(board, 0)).isInstanceOf(EventsGoneException.class);
    }

    @Test
    void purgedPrefixIsGone() {
        log.append(8);
        log.retained.removeIf(e -> e.seq() <= 4); // purga: se conserva desde el seq 5

        assertThatThrownBy(() -> service.execute(board, 3)).isInstanceOf(EventsGoneException.class)
                .hasMessageContaining("no longer retained");
        // since == primer retenido - 1: aun cubierto
        assertThat(service.execute(board, 4).events()).extracting(e -> e.seq()).containsExactly(5L, 6L, 7L, 8L);
    }

    @Test
    void fullyPurgedLogIsGoneUnlessUpToDate() {
        log.append(3);
        log.retained.clear();

        assertThatThrownBy(() -> service.execute(board, 1)).isInstanceOf(EventsGoneException.class);
        assertThat(service.execute(board, 3).events()).isEmpty();
    }

    @Test
    void unknownBoardIsNotFound() {
        assertThatThrownBy(() -> service.execute(UUID.randomUUID(), 0)).isInstanceOf(NotFoundException.class);
    }
}
