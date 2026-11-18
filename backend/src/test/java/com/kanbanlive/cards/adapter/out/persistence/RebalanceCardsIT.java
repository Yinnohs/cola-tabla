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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** B1.7: al agotarse el espacio de posiciones se rebalancea la lista (varias filas) en la misma transaccion. */
@DataJpaTest
@Import({CardPersistenceAdapter.class, CardEntityMapper.class, CreateCardService.class, MoveCardService.class,
        RebalanceCardsIT.Beans.class})
class RebalanceCardsIT extends PostgresIT {

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

    UUID list;
    final String stem = "1".repeat(31);

    @BeforeEach
    void parents() {
        UUID workspaceId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        list = UUID.randomUUID();
        jdbc.update("insert into workspace(id, name) values (?, 'w')", workspaceId);
        jdbc.update("insert into board(id, workspace_id, name) values (?, ?, 'b')", boardId, workspaceId);
        jdbc.update("insert into board_list(id, board_id, name, position) values (?, ?, 'A', 'V')", list, boardId);
    }

    UUID rawCard(String title, String position) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into card(id, list_id, title, position) values (?, ?, ?, ?)", id, list, title, position);
        return id;
    }

    List<String> titlesInOrder() {
        return jdbc.queryForList("select title from card where list_id = ? order by position", String.class, list);
    }

    List<String> positionsInOrder() {
        return jdbc.queryForList("select position from card where list_id = ? order by position", String.class,
                list);
    }

    Long version(UUID id) {
        return jdbc.queryForObject("select version from card where id = ?", Long.class, id);
    }

    @Test
    void movingBetweenExhaustedNeighboursRewritesPositionsKeepingOrderAndBumpingVersions() {
        UUID a = rawCard("A", stem + "1");
        UUID b = rawCard("B", stem + "2");
        UUID c = rawCard("C", "z");
        UUID d = rawCard("D", "zz");

        CardSummary moved = move.execute(new MoveCardCommand(c, list, a, b));

        assertThat(titlesInOrder()).containsExactly("A", "C", "B", "D");
        assertThat(positionsInOrder()).doesNotHaveDuplicates().allMatch(p -> p.length() < 32);
        // A, B y D solo se reescribieron en el rebalanceo (+1); C ademas se movio (+1)
        assertThat(version(a)).isEqualTo(1L);
        assertThat(version(b)).isEqualTo(1L);
        assertThat(version(d)).isEqualTo(1L);
        assertThat(version(c)).isEqualTo(2L);
        assertThat(moved.version()).isEqualTo(2L);
    }

    @Test
    void rebalancedPositionsAreEvenlySpacedAndTheNewCardGoesLast() {
        rawCard("A", stem + "1");
        rawCard("B", stem + "2");
        rawCard("C", "z".repeat(32));

        CardSummary created = create.execute(new CreateCardCommand(list, "next", null));

        assertThat(titlesInOrder()).containsExactly("A", "B", "C", "next");
        List<String> positions = positionsInOrder();
        // evenlySpaced(3): misma longitud para las tres reescritas
        assertThat(positions.subList(0, 3)).allMatch(p -> p.length() == positions.get(0).length());
        assertThat(created.position()).isEqualTo(positions.get(3)).isGreaterThan(positions.get(2));
    }
}
