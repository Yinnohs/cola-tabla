package com.kanbanlive;

import static org.assertj.core.api.Assertions.assertThat;

import com.kanbanlive.testsupport.PostgresIT;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Requiere Docker. Valida el esquema de Fase 1 (V2..V5) contra Postgres real. */
@SpringBootTest
class SchemaMigrationIT extends PostgresIT {

    @Autowired
    DataSource dataSource;

    @Test
    void allMigrationsApplied() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success and version in ('1','2','3','4','5')",
                Integer.class);
        assertThat(applied).isEqualTo(5);
    }

    @Test
    void positionColumnsUseCCollationAndIndexes() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<String> collations = jdbc.queryForList(
                "select collation_name from information_schema.columns "
                        + "where column_name = 'position' and table_name in ('card','board_list')", String.class);
        assertThat(collations).hasSize(2).containsOnly("C");
        List<String> indexes = jdbc.queryForList(
                "select indexname from pg_indexes where indexname in ('idx_card_list_position','idx_board_list_board_position')",
                String.class);
        assertThat(indexes).hasSize(2);
    }

    @Test
    void positionsAreOrderedByBytes() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID ws = UUID.randomUUID();
        UUID board = UUID.randomUUID();
        UUID list = UUID.randomUUID();
        jdbc.update("insert into workspace(id, name) values (?, 'w')", ws);
        jdbc.update("insert into board(id, workspace_id, name) values (?, ?, 'b')", board, ws);
        jdbc.update("insert into board_list(id, board_id, name, position) values (?, ?, 'l', 'V')", list, board);
        for (String p : List.of("a", "B", "1", "Vz")) {
            jdbc.update("insert into card(id, list_id, title, position) values (?, ?, 't', ?)",
                    UUID.randomUUID(), list, p);
        }
        List<String> ordered = jdbc.queryForList(
                "select position from card where list_id = ? order by position", String.class, list);
        assertThat(ordered).containsExactly("1", "B", "Vz", "a");
    }
}
