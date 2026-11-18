package com.kanbanlive.boards.adapter.out.persistence;

import com.kanbanlive.boards.application.port.in.BoardDetails;
import com.kanbanlive.boards.application.port.in.BoardDetails.CardDetails;
import com.kanbanlive.boards.application.port.in.BoardDetails.ListDetails;
import com.kanbanlive.boards.application.port.out.BoardDetailsReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Proyeccion de solo lectura sobre {@code board}, {@code board_list} y {@code card}. Lee las tablas por SQL
 * (sin tocar dominio ni adapters de lists/cards). Orden por {@code position} (columna COLLATE "C").
 */
@Repository
class BoardDetailsJdbcReader implements BoardDetailsReader {

    private final JdbcTemplate jdbc;

    BoardDetailsJdbcReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BoardDetails> findById(UUID boardId) {
        List<BoardRow> boards = jdbc.query(
                "select id, workspace_id, name, version from board where id = ?",
                (rs, i) -> new BoardRow(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
                        rs.getString("name"), rs.getLong("version")),
                boardId);
        if (boards.isEmpty()) {
            return Optional.empty();
        }
        BoardRow board = boards.get(0);

        Map<UUID, List<CardDetails>> cardsByList = new HashMap<>();
        jdbc.query("""
                select c.id, c.list_id, c.title, c.description, c.position, c.version
                from card c join board_list l on l.id = c.list_id
                where l.board_id = ?
                order by c.position, c.id
                """, rs -> {
            cardsByList.computeIfAbsent(rs.getObject("list_id", UUID.class), k -> new ArrayList<>())
                    .add(new CardDetails(rs.getObject("id", UUID.class), rs.getString("title"),
                            rs.getString("description"), rs.getString("position"), rs.getLong("version")));
        }, boardId);

        List<ListDetails> lists = jdbc.query(
                "select id, name, position, version from board_list where board_id = ? order by position, id",
                (rs, i) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new ListDetails(id, rs.getString("name"), rs.getString("position"), rs.getLong("version"),
                            cardsByList.getOrDefault(id, List.of()));
                },
                boardId);

        return Optional.of(new BoardDetails(board.id(), board.workspaceId(), board.name(), board.version(), lists));
    }

    private record BoardRow(UUID id, UUID workspaceId, String name, long version) {
    }
}
