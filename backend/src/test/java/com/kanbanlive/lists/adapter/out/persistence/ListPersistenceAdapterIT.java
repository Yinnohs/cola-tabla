package com.kanbanlive.lists.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.lists.domain.model.BoardList;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import com.kanbanlive.testsupport.PostgresIT;
import com.kanbanlive.testsupport.SqlStatementRecorder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = SqlStatementRecorder.PROPERTY)
@Import({ListPersistenceAdapter.class, ListEntityMapper.class})
class ListPersistenceAdapterIT extends PostgresIT {

    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    ListPersistenceAdapter adapter;
    @Autowired
    JdbcTemplate jdbc;

    UUID boardId;
    UUID otherBoardId;

    @BeforeEach
    void parents() {
        UUID workspaceId = UUID.randomUUID();
        boardId = UUID.randomUUID();
        otherBoardId = UUID.randomUUID();
        jdbc.update("insert into workspace(id, name) values (?, 'w')", workspaceId);
        jdbc.update("insert into board(id, workspace_id, name) values (?, ?, 'b1')", boardId, workspaceId);
        jdbc.update("insert into board(id, workspace_id, name) values (?, ?, 'b2')", otherBoardId, workspaceId);
    }

    BoardList newList(String name, String position) {
        return adapter.save(BoardList.create(UUID.randomUUID(), boardId, name, FractionalIndex.of(position), NOW));
    }

    @Test
    void savesAndLoadsRoundTrip() {
        UUID id = UUID.randomUUID();
        BoardList saved = adapter.save(BoardList.create(id, boardId, "Todo", FractionalIndex.of("V"), NOW));
        assertThat(saved.version()).isZero();

        BoardList loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.name()).isEqualTo("Todo");
        assertThat(loaded.boardId()).isEqualTo(boardId);
        assertThat(loaded.position().value()).isEqualTo("V");
        assertThat(adapter.existsById(id)).isTrue();
        assertThat(adapter.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void renameBumpsVersion() {
        BoardList created = newList("Old", "V");
        BoardList renamed = adapter.save(created.rename("New", NOW.plusSeconds(1)));

        assertThat(renamed.version()).isEqualTo(1);
        assertThat(adapter.findById(created.id()).orElseThrow().name()).isEqualTo("New");
    }

    @Test
    void moveIsASingleUpdateOfThatRowAndBumpsVersion() {
        BoardList a = newList("A", "G");
        BoardList b = newList("B", "V");
        BoardList c = newList("C", "k");

        SqlStatementRecorder.clear();
        BoardList moved = adapter.save(c.moveTo(FractionalIndex.of("M"), NOW.plusSeconds(1)));

        assertThat(SqlStatementRecorder.statementsStartingWith("update")).hasSize(1);
        assertThat(moved.version()).isEqualTo(1);
        assertThat(adapter.findById(c.id()).orElseThrow().position().value()).isEqualTo("M");
        assertThat(adapter.findById(a.id()).orElseThrow().version()).isZero();
        assertThat(adapter.findById(b.id()).orElseThrow().version()).isZero();
    }

    @Test
    void savingWithOutdatedVersionThrowsStaleVersion() {
        BoardList v0 = newList("A", "V");
        adapter.save(v0.rename("B", NOW));

        assertThatThrownBy(() -> adapter.save(v0.rename("C", NOW))).isInstanceOf(StaleVersionException.class);
    }

    @Test
    void lastPositionUsesBinaryCollationOrder() {
        assertThat(adapter.findLastPosition(boardId)).isEmpty();
        newList("a", "V");
        newList("b", "a");
        newList("c", "B");

        assertThat(adapter.findLastPosition(boardId)).contains(FractionalIndex.of("a"));
        assertThat(adapter.findLastPosition(otherBoardId)).isEmpty();
    }

    @Test
    void findPositionInBoardOnlyMatchesTheOwningBoard() {
        BoardList l = newList("A", "V");
        assertThat(adapter.findPositionInBoard(boardId, l.id())).contains(FractionalIndex.of("V"));
        assertThat(adapter.findPositionInBoard(otherBoardId, l.id())).isEmpty();
    }
}
