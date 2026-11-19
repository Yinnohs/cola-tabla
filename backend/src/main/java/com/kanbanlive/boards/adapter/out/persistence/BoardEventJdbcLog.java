package com.kanbanlive.boards.adapter.out.persistence;

import com.kanbanlive.boards.application.port.in.BoardEventRecord;
import com.kanbanlive.boards.application.port.out.BoardEventLog;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * El UPDATE ... RETURNING toma el lock de fila del tablero hasta el commit: serializa a los escritores del mismo
 * tablero, de modo que el orden de seq coincide con el orden de commit y no hay huecos (un rollback deshace
 * tambien el incremento).
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
class BoardEventJdbcLog implements BoardEventLog {

    private final JdbcTemplate jdbc;

    BoardEventJdbcLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long append(UUID boardId, String type, String payloadJson) {
        List<Long> seq = jdbc.queryForList(
                "UPDATE board SET event_seq = event_seq + 1 WHERE id = ? RETURNING event_seq", Long.class, boardId);
        if (seq.isEmpty()) {
            throw new NotFoundException("board " + boardId + " not found");
        }
        jdbc.update("INSERT INTO board_event (board_id, seq, type, payload) VALUES (?, ?, ?, ?::jsonb)",
                boardId, seq.get(0), type, payloadJson);
        return seq.get(0);
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public Optional<Long> currentSeq(UUID boardId) {
        return jdbc.queryForList("SELECT event_seq FROM board WHERE id = ?", Long.class, boardId).stream()
                .findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public List<BoardEventRecord> findAfter(UUID boardId, long since, long upTo, int limit) {
        return jdbc.query("SELECT seq, type, payload::text AS payload, created_at FROM board_event "
                        + "WHERE board_id = ? AND seq > ? AND seq <= ? ORDER BY seq LIMIT ?",
                (rs, i) -> new BoardEventRecord(boardId, rs.getLong("seq"), rs.getString("type"),
                        rs.getString("payload"), rs.getTimestamp("created_at").toInstant()),
                boardId, since, upTo, limit);
    }

    /** Transaccion propia por lote (REQUIRED): una purga larga no retiene locks. Usa idx_board_event_created_at. */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public int deleteOlderThan(Instant cutoff, int batchSize) {
        return jdbc.update("DELETE FROM board_event WHERE ctid IN ("
                        + "SELECT ctid FROM board_event WHERE created_at < ? ORDER BY created_at LIMIT ?)",
                Timestamp.from(cutoff), batchSize);
    }
}
