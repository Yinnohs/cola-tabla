package com.kanbanlive;

import static org.assertj.core.api.Assertions.assertThat;

import com.kanbanlive.testsupport.PostgresIT;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Requiere Docker. Arranca el contexto completo contra Postgres real y valida que Flyway migro. */
@SpringBootTest
class KanbanLiveApplicationIT extends PostgresIT {

    @Autowired
    DataSource dataSource;

    @Test
    void contextStartsAndFlywayAppliedV1() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version = '1' and success", Integer.class);
        assertThat(applied).isEqualTo(1);
    }
}
