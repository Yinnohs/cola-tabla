package com.kanbanlive.boards.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.boards.application.port.in.BoardDetails;
import com.kanbanlive.boards.domain.model.Board;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import com.kanbanlive.testsupport.PostgresIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import({BoardPersistenceAdapter.class, BoardEntityMapper.class, BoardDetailsJdbcReader.class})
class BoardPersistenceAdapterIT extends PostgresIT {

    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    BoardPersistenceAdapter adapter;
    @Autowired
    BoardDetailsJdbcReader reader;
    @Autowired
    JdbcTemplate jdbc;

    UUID workspaceId;

    @BeforeEach
    void workspace() {
        workspaceId = UUID.randomUUID();
        jdbc.update("insert into workspace(id, name) values (?, 'w')", workspaceId);
    }

    @Test
    void renameBumpsVersionAndPersists() {
        UUID id = UUID.randomUUID();
        Board created = adapter.save(Board.create(id, workspaceId, "Old", NOW));
        assertThat(created.version()).isZero();

        Board renamed = adapter.save(created.rename("New", NOW.plusSeconds(1)));

        assertThat(renamed.version()).isEqualTo(1);
        Board loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.name()).isEqualTo("New");
        assertThat(loaded.workspaceId()).isEqualTo(workspaceId);
        assertThat(loaded.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void savingWithOutdatedVersionThrowsStaleVersion() {
        UUID id = UUID.randomUUID();
        Board v0 = adapter.save(Board.create(id, workspaceId, "A", NOW));
        adapter.save(v0.rename("B", NOW));

        assertThatThrownBy(() -> adapter.save(v0.rename("C", NOW))).isInstanceOf(StaleVersionException.class);
    }

    @Test
    void detailsReadsListsAndCardsOrderedByPosition() {
        UUID boardId = UUID.randomUUID();
        adapter.save(Board.create(boardId, workspaceId, "Board", NOW));
        UUID listB = UUID.randomUUID();
        UUID listA = UUID.randomUUID();
        jdbc.update("insert into board_list(id, board_id, name, position) values (?, ?, 'second', 'b')", listB, boardId);
        jdbc.update("insert into board_list(id, board_id, name, position) values (?, ?, 'first', 'V')", listA, boardId);
        jdbc.update("insert into card(id, list_id, title, description, position) values (?, ?, 'c2', 'd', 'a')",
                UUID.randomUUID(), listA);
        jdbc.update("insert into card(id, list_id, title, position) values (?, ?, 'c1', 'B')", UUID.randomUUID(), listA);

        BoardDetails details = reader.findById(boardId).orElseThrow();

        assertThat(details.name()).isEqualTo("Board");
        assertThat(details.lists()).extracting(BoardDetails.ListDetails::name).containsExactly("first", "second");
        assertThat(details.lists().get(0).cards()).extracting(BoardDetails.CardDetails::title)
                .containsExactly("c1", "c2");
        assertThat(details.lists().get(0).cards().get(1).description()).isEqualTo("d");
        assertThat(details.lists().get(1).cards()).isEmpty();
    }

    @Test
    void existsByIdReflectsPersistedBoards() {
        UUID id = UUID.randomUUID();
        adapter.save(Board.create(id, workspaceId, "B", NOW));
        assertThat(adapter.existsById(id)).isTrue();
        assertThat(adapter.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void detailsOfUnknownBoardIsEmpty() {
        assertThat(reader.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByWorkspaceIdReturnsOwnBoardsOrderedByCreatedAtThenId() {
        UUID otherWorkspace = UUID.randomUUID();
        jdbc.update("insert into workspace(id, name) values (?, 'other')", otherWorkspace);
        UUID idLow = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID idHigh = UUID.fromString("00000000-0000-0000-0000-000000000002");
        adapter.save(Board.create(UUID.randomUUID(), workspaceId, "newest", NOW.plusSeconds(10)));
        adapter.save(Board.create(idHigh, workspaceId, "tie-high", NOW));
        adapter.save(Board.create(idLow, workspaceId, "tie-low", NOW));
        adapter.save(Board.create(UUID.randomUUID(), otherWorkspace, "foreign", NOW));

        List<Board> boards = adapter.findByWorkspaceId(workspaceId);

        assertThat(boards).extracting(Board::name).containsExactly("tie-low", "tie-high", "newest");
        assertThat(adapter.findByWorkspaceId(UUID.randomUUID())).isEmpty();
    }
}
