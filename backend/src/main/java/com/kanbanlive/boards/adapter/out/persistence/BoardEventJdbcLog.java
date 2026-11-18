package com.kanbanlive.boards.adapter.out.persistence;

import com.kanbanlive.boards.application.port.out.BoardEventLog;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import java.util.List;
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
}
