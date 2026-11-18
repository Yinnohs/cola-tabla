package com.kanbanlive.boards.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.testsupport.PostgresIT;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** B3.3: seq por tablero dentro de la tx, log en board_event. */
@DataJpaTest
@Import(BoardEventJdbcLog.class)
class BoardEventJdbcLogIT extends PostgresIT {

    @Autowired
    BoardEventJdbcLog log;
    @Autowired
    JdbcTemplate jdbc;

    UUID boardA;
    UUID boardB;

    @BeforeEach
    void boards() {
        UUID ws = UUID.randomUUID();
        boardA = UUID.randomUUID();
        boardB = UUID.randomUUID();
        jdbc.update("insert into workspace(id, name) values (?, 'w')", ws);
        jdbc.update("insert into board(id, workspace_id, name) values (?, ?, 'a')", boardA, ws);
        jdbc.update("insert into board(id, workspace_id, name) values (?, ?, 'b')", boardB, ws);
    }

    @Test
    void sequenceIsPerBoardAndGapFree() {
        assertThat(log.append(boardA, "card.created", "{\"n\":1}")).isEqualTo(1);
        assertThat(log.append(boardA, "card.moved", "{\"n\":2}")).isEqualTo(2);
        assertThat(log.append(boardB, "list.created", "{}")).isEqualTo(1);
        assertThat(log.append(boardA, "card.deleted", "{}")).isEqualTo(3);

        assertThat(jdbc.queryForObject("select event_seq from board where id = ?", Long.class, boardA)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select event_seq from board where id = ?", Long.class, boardB)).isEqualTo(1);
    }

    @Test
    void rowIsStoredWithTypeAndJsonbPayload() {
        log.append(boardA, "card.created", "{\"title\":\"x\"}");

        Map<String, Object> row = jdbc.queryForMap(
                "select seq, type, payload->>'title' as title, created_at from board_event where board_id = ?", boardA);
        assertThat(row.get("seq")).isEqualTo(1L);
        assertThat(row.get("type")).isEqualTo("card.created");
        assertThat(row.get("title")).isEqualTo("x");
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void unknownBoardIsNotFound() {
        assertThatThrownBy(() -> log.append(UUID.randomUUID(), "x", "{}")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void invalidJsonIsRejected() {
        assertThatThrownBy(() -> log.append(boardA, "x", "not json"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateSeqIsImpossible() {
        log.append(boardA, "x", "{}");
        assertThatThrownBy(() -> jdbc.update(
                "insert into board_event(board_id, seq, type, payload) values (?, 1, 'x', '{}')", boardA))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void requiresAnOngoingTransaction() {
        assertThatThrownBy(() -> log.append(boardA, "x", "{}")).isInstanceOf(IllegalTransactionStateException.class);
    }
}
