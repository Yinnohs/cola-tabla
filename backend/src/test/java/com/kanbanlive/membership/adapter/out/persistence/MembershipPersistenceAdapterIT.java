package com.kanbanlive.membership.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.kanbanlive.membership.domain.model.Membership;
import com.kanbanlive.membership.domain.model.Role;
import com.kanbanlive.testsupport.PostgresIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import({MembershipPersistenceAdapter.class, MembershipEntityMapper.class})
class MembershipPersistenceAdapterIT extends PostgresIT {

    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    MembershipPersistenceAdapter adapter;
    @Autowired
    JdbcTemplate jdbc;

    UUID workspace() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into workspace (id, name) values (?, ?)", id, "w");
        return id;
    }

    @Test
    void roundTripChangeRoleAndCount() {
        UUID ws = workspace();
        Membership saved = adapter.save(Membership.create(UUID.randomUUID(), ws, "u1", Role.OWNER, NOW));
        assertThat(saved.version()).isZero();

        Membership loaded = adapter.findByWorkspaceAndUser(ws, "u1").orElseThrow();
        assertThat(loaded.role()).isEqualTo(Role.OWNER);
        assertThat(adapter.countByWorkspaceAndRole(ws, Role.OWNER)).isEqualTo(1);
        assertThat(adapter.findByWorkspaceAndUser(ws, "other")).isEmpty();

        loaded.changeRole(Role.VIEWER, NOW.plusSeconds(5));
        adapter.save(loaded);
        assertThat(adapter.findByWorkspaceAndUser(ws, "u1").orElseThrow().role()).isEqualTo(Role.VIEWER);
        assertThat(adapter.countByWorkspaceAndRole(ws, Role.OWNER)).isZero();
    }
}
