package com.kanbanlive.boards.adapter.in.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.kanbanlive.testsupport.PostgresIT;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * B4.3: unico test con el scheduling ACTIVADO (en el resto de tests {@code kanban.scheduling.enabled=false}). El
 * job real, planificado por Spring, purga en la BD real los eventos mas viejos que la retencion y respeta el resto.
 */
@SpringBootTest(properties = {
        "kanban.scheduling.enabled=true",
        "kanban.events.retention=PT1H",
        "kanban.events.purge.initial-delay=PT0S",
        "kanban.events.purge.interval=PT0.2S",
        "kanban.events.purge.batch-size=2",
        "kanban.events.purge.max-batches=50"})
class BoardEventPurgeJobIT extends PostgresIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void theScheduledJobPurgesEventsOlderThanTheRetentionInBoundedBatches() {
        UUID ws = UUID.randomUUID();
        UUID board = UUID.randomUUID();
        jdbc.update("insert into workspace(id, name) values (?, 'w')", ws);
        jdbc.update("insert into board(id, workspace_id, name, event_seq) values (?, ?, 'b', 7)", board, ws);
        Instant now = Instant.now();
        for (int seq = 1; seq <= 5; seq++) { // 5 viejos (mas lotes que batch-size=2)
            insert(board, seq, now.minus(Duration.ofHours(3)));
        }
        insert(board, 6, now.minus(Duration.ofMinutes(10))); // dentro de la retencion
        insert(board, 7, now);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<Long> left = jdbc.queryForList("select seq from board_event where board_id = ? order by seq",
                    Long.class, board);
            assertThat(left).containsExactly(6L, 7L);
        });
    }

    private void insert(UUID board, int seq, Instant createdAt) {
        jdbc.update("insert into board_event(board_id, seq, type, payload, created_at) values (?, ?, 'x', '{}', ?)",
                board, seq, Timestamp.from(createdAt));
    }
}
