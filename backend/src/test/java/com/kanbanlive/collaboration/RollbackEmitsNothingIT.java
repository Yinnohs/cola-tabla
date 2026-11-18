package com.kanbanlive.collaboration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanbanlive.sharedkernel.application.BoardChangePublisher;
import com.kanbanlive.testsupport.PostgresIT;
import com.kanbanlive.testsupport.StompTestClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * B3.7 - un rollback no emite evento (listener AFTER_COMMIT) ni deja fila en board_event, y el seq no se consume.
 * Modo local (seguridad desactivada) para centrar el test en la mensajeria.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "kanban.security.enabled=false")
class RollbackEmitsNothingIT extends PostgresIT {

    @LocalServerPort
    int port;
    @Autowired
    BoardChangePublisher publisher;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper mapper;

    @Test
    void rolledBackTransactionEmitsNoEventAndPersistsNoRow() throws Exception {
        UUID ws = UUID.randomUUID();
        UUID board = UUID.randomUUID();
        jdbc.update("insert into workspace(id, name) values (?, 'w')", ws);
        jdbc.update("insert into board(id, workspace_id, name) values (?, ?, 'b')", board, ws);

        try (StompTestClient c = StompTestClient.connect(port, null)) {
            assertThat(c.awaitConnected()).isTrue();
            c.subscribe("/topic/boards." + board);
            Thread.sleep(500);

            // 1) La tx falla tras registrar el evento: nada se difunde ni se persiste.
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                publisher.publish(board, "card.created", Map.of("card", "ghost"));
                throw new IllegalStateException("boom");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(c.nextMessage(1500)).as("no event for rolled back tx").isNull();
            assertThat(jdbc.queryForObject("select count(*) from board_event where board_id = ?", Long.class, board))
                    .isZero();
            assertThat(jdbc.queryForObject("select event_seq from board where id = ?", Long.class, board)).isZero();

            // 2) Una tx confirmada despues emite el seq 1: el rollback no consumio numeros (sin huecos).
            tx.executeWithoutResult(status -> publisher.publish(board, "card.created", Map.of("card", "real")));
            JsonNode event = mapper.readTree(c.nextMessage(5000));
            assertThat(event.get("seq").asLong()).isEqualTo(1L);
            assertThat(event.at("/payload/card").asText()).isEqualTo("real");
        }
    }
}
