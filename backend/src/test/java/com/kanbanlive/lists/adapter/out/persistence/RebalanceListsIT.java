package com.kanbanlive.lists.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.kanbanlive.lists.application.port.in.CreateListUseCase.CreateListCommand;
import com.kanbanlive.lists.application.port.in.ListSummary;
import com.kanbanlive.lists.application.port.in.MoveListUseCase.MoveListCommand;
import com.kanbanlive.lists.application.port.out.BoardDirectory;
import com.kanbanlive.lists.application.service.CreateListService;
import com.kanbanlive.lists.application.service.MoveListService;
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

/** B1.7: al agotarse el espacio de posiciones se rebalancea el tablero (varias filas) en la misma transaccion. */
@DataJpaTest
@Import({ListPersistenceAdapter.class, ListEntityMapper.class, CreateListService.class, MoveListService.class,
        RebalanceListsIT.Beans.class})
class RebalanceListsIT extends PostgresIT {

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
        BoardDirectory boardDirectory() {
            return boardId -> true;
        }

        @Bean
        com.kanbanlive.sharedkernel.application.BoardChangePublisher boardChangePublisher() {
            return (boardId, type, payload) -> { };
        }
    }

    @Autowired
    CreateListService create;
    @Autowired
    MoveListService move;
    @Autowired
    JdbcTemplate jdbc;

    UUID board;
    final String stem = "1".repeat(31);

    @BeforeEach
    void parents() {
        UUID workspaceId = UUID.randomUUID();
        board = UUID.randomUUID();
        jdbc.update("insert into workspace(id, name) values (?, 'w')", workspaceId);
        jdbc.update("insert into board(id, workspace_id, name) values (?, ?, 'b')", board, workspaceId);
    }

    UUID rawList(String name, String position) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into board_list(id, board_id, name, position) values (?, ?, ?, ?)", id, board, name,
                position);
        return id;
    }

    List<String> namesInOrder() {
        return jdbc.queryForList("select name from board_list where board_id = ? order by position", String.class,
                board);
    }

    Long version(UUID id) {
        return jdbc.queryForObject("select version from board_list where id = ?", Long.class, id);
    }

    @Test
    void movingBetweenExhaustedNeighboursRebalancesAndKeepsOrder() {
        UUID a = rawList("A", stem + "1");
        UUID b = rawList("B", stem + "2");
        UUID c = rawList("C", "z");

        ListSummary moved = move.execute(new MoveListCommand(c, a, b));

        assertThat(namesInOrder()).containsExactly("A", "C", "B");
        assertThat(jdbc.queryForList("select position from board_list where board_id = ?", String.class, board))
                .doesNotHaveDuplicates().allMatch(p -> p.length() < 32);
        assertThat(version(a)).isEqualTo(1L);
        assertThat(version(b)).isEqualTo(1L);
        assertThat(version(c)).isEqualTo(2L);
        assertThat(moved.version()).isEqualTo(2L);
    }

    @Test
    void creatingAtTheEndOfAnExhaustedBoardRebalancesFirst() {
        UUID a = rawList("A", "1");
        UUID b = rawList("B", "z".repeat(32));

        ListSummary created = create.execute(new CreateListCommand(board, "next"));

        assertThat(namesInOrder()).containsExactly("A", "B", "next");
        assertThat(version(a)).isEqualTo(1L);
        assertThat(version(b)).isEqualTo(1L);
        assertThat(created.version()).isZero();
        assertThat(created.position().length()).isLessThan(32);
    }
}
