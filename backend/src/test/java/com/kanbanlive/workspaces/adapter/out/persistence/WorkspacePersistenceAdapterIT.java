package com.kanbanlive.workspaces.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.kanbanlive.testsupport.PostgresIT;
import com.kanbanlive.workspaces.domain.model.Workspace;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({WorkspacePersistenceAdapter.class, WorkspaceEntityMapper.class})
class WorkspacePersistenceAdapterIT extends PostgresIT {

    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    WorkspacePersistenceAdapter adapter;

    @Test
    void savesAndLoadsRoundTrip() {
        UUID id = UUID.randomUUID();
        Workspace saved = adapter.save(Workspace.create(id, "Acme", NOW));

        assertThat(saved.version()).isZero();
        Workspace loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.name()).isEqualTo("Acme");
        assertThat(loaded.createdAt()).isEqualTo(NOW);
        assertThat(adapter.existsById(id)).isTrue();
        assertThat(adapter.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void listIsOrderedByCreationThenId() {
        Workspace second = adapter.save(Workspace.create(UUID.randomUUID(), "second", NOW.plusSeconds(10)));
        Workspace first = adapter.save(Workspace.create(UUID.randomUUID(), "first", NOW.plusSeconds(5)));

        var all = adapter.findAll();
        assertThat(all).extracting(Workspace::id).containsSubsequence(first.id(), second.id());
    }
}
