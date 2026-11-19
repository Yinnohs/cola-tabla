package com.kanbanlive.cards.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.kanbanlive.cards.application.port.in.CardSummary;
import com.kanbanlive.cards.application.port.in.CreateCardUseCase.CreateCardCommand;
import com.kanbanlive.cards.application.port.in.MoveCardUseCase.MoveCardCommand;
import com.kanbanlive.cards.application.port.out.ListDirectory;
import com.kanbanlive.cards.application.service.CreateCardService;
import com.kanbanlive.cards.application.service.MoveCardService;
import com.kanbanlive.sharedkernel.application.Clock;
import com.kanbanlive.sharedkernel.application.IdGenerator;
import com.kanbanlive.testsupport.PostgresIT;
import com.kanbanlive.testsupport.SqlStatementRecorder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DoD de la Fase 1: mover una tarjeta es UN solo UPDATE sobre su fila. Se ejercita el caso de uso real
 * (MoveCardService + adapter JPA + Postgres) y se cuentan las sentencias que emite Hibernate.
 */
@DataJpaTest(properties = SqlStatementRecorder.PROPERTY)
@Import({CardPersistenceAdapter.class, CardEntityMapper.class, CreateCardService.class, MoveCardService.class,
        MoveCardSingleUpdateIT.Beans.class})
class MoveCardSingleUpdateIT extends PostgresIT {

    @TestConfiguration
    static class Beans {
        @Bean
        Clock clock() {
            return Instant::now;
        }

        @Bean
        IdGenerator ids() {
            return UUID::randomUUID;
        }

        @Bean
        ListDirectory listDirectory() {
            return new ListDirectory() {
                @Override
                public boolean exists(UUID listId) {
                    return true;
                }

                @Override
                public java.util.Optional<UUID> boardIdOf(UUID listId) {
                    return java.util.Optional.of(UUID.randomUUID());
                }
            };
        }

        @Bean
        com.kanbanlive.sharedkernel.application.BoardChangePublisher boardChangePublisher() {
            return (boardId, type, payload) -> { };
        }
    }

    @Autowired
    CreateCardService create;
    @Autowired
    MoveCardService move;
    @Autowired
    JdbcTemplate jdbc;

    UUID listA;
    UUID listB;

    @BeforeEach
    void parents() {
        UUID workspaceId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        listA = UUID.randomUUID();
        listB = UUID.randomUUID();
        jdbc.update("insert into workspace(id, name) values (?, 'w')", workspaceId);
        jdbc.update("insert into board(id, workspace_id, name) values (?, ?, 'b')", boardId, workspaceId);
        jdbc.update("insert into board_list(id, board_id, name, position) values (?, ?, 'A', 'V')", listA, boardId);
        jdbc.update("insert into board_list(id, board_id, name, position) values (?, ?, 'B', 'k')", listB, boardId);
    }

    CardSummary card(UUID list, String title) {
        return create.execute(new CreateCardCommand(list, title, null));
    }

    Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("select list_id, position, version from card where id = ?", id);
    }

    @Test
    void movingACardIssuesExactlyOneUpdateOnItsRowAndBumpsVersion() {
        CardSummary c1 = card(listA, "c1");
        CardSummary c2 = card(listA, "c2");
        CardSummary c3 = card(listA, "c3");
        CardSummary c4 = card(listA, "c4");
        Map<String, Object> c1Before = row(c1.id());
        Map<String, Object> c2Before = row(c2.id());
        Map<String, Object> c3Before = row(c3.id());

        SqlStatementRecorder.clear();
        CardSummary moved = move.execute(new MoveCardCommand(c4.id(), listA, c1.id(), c2.id()));

        List<String> updates = SqlStatementRecorder.statementsStartingWith("update");
        assertThat(updates).hasSize(1);
        assertThat(updates.get(0)).startsWith("update card set").contains("where id = ?")
                // B4.4: mover es last-write-wins, sin clausula de version en el WHERE
                .doesNotContain("and version")
                .contains("version = version + 1");
        assertThat(SqlStatementRecorder.statementsStartingWith("insert")).isEmpty();
        assertThat(SqlStatementRecorder.statementsStartingWith("delete")).isEmpty();

        assertThat(moved.version()).isEqualTo(c4.version() + 1);
        assertThat(row(c4.id()).get("version")).isEqualTo(1L);
        assertThat(row(c4.id()).get("position")).isEqualTo(moved.position());
        assertThat(moved.position()).isGreaterThan(c1.position()).isLessThan(c2.position());
        // las vecinas no se tocan
        assertThat(row(c1.id())).isEqualTo(c1Before);
        assertThat(row(c2.id())).isEqualTo(c2Before);
        assertThat(row(c3.id())).isEqualTo(c3Before);
    }

    @Test
    void movingAcrossListsIsAlsoASingleUpdate() {
        CardSummary a = card(listA, "a");
        CardSummary x = card(listB, "x");
        CardSummary y = card(listB, "y");

        SqlStatementRecorder.clear();
        CardSummary moved = move.execute(new MoveCardCommand(a.id(), listB, x.id(), y.id()));

        assertThat(SqlStatementRecorder.statementsStartingWith("update")).hasSize(1);
        assertThat(moved.listId()).isEqualTo(listB);
        assertThat(row(a.id()).get("list_id")).isEqualTo(listB);
        assertThat(row(a.id()).get("version")).isEqualTo(1L);
    }
}
