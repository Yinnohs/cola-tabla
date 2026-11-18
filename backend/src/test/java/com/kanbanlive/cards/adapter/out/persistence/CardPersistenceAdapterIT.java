package com.kanbanlive.cards.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.cards.domain.model.Card;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import com.kanbanlive.testsupport.PostgresIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@Import({CardPersistenceAdapter.class, CardEntityMapper.class})
class CardPersistenceAdapterIT extends PostgresIT {

    static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    CardPersistenceAdapter adapter;
    @Autowired
    JdbcTemplate jdbc;

    UUID listId;
    UUID otherListId;

    @BeforeEach
    void parents() {
        UUID workspaceId = UUID.randomUUID();
        UUID boardId = UUID.randomUUID();
        listId = UUID.randomUUID();
        otherListId = UUID.randomUUID();
        jdbc.update("insert into workspace(id, name) values (?, 'w')", workspaceId);
        jdbc.update("insert into board(id, workspace_id, name) values (?, ?, 'b')", boardId, workspaceId);
        jdbc.update("insert into board_list(id, board_id, name, position) values (?, ?, 'l1', 'V')", listId, boardId);
        jdbc.update("insert into board_list(id, board_id, name, position) values (?, ?, 'l2', 'k')", otherListId,
                boardId);
    }

    Card newCard(String title, String position) {
        return adapter.save(Card.create(UUID.randomUUID(), listId, title, null, FractionalIndex.of(position), NOW));
    }

    @Test
    void savesAndLoadsRoundTrip() {
        UUID id = UUID.randomUUID();
        Card saved = adapter.save(Card.create(id, listId, "T", "desc", FractionalIndex.of("V"), NOW));
        assertThat(saved.version()).isZero();

        Card loaded = adapter.findById(id).orElseThrow();
        assertThat(loaded.title()).isEqualTo("T");
        assertThat(loaded.description()).isEqualTo("desc");
        assertThat(loaded.listId()).isEqualTo(listId);
        assertThat(loaded.position().value()).isEqualTo("V");
        assertThat(loaded.createdAt()).isEqualTo(NOW);
    }

    @Test
    void updateBumpsVersionAndPersists() {
        Card created = newCard("Old", "V");
        Card updated = adapter.save(created.update("New", "d", NOW.plusSeconds(1)));

        assertThat(updated.version()).isEqualTo(1);
        Card loaded = adapter.findById(created.id()).orElseThrow();
        assertThat(loaded.title()).isEqualTo("New");
        assertThat(loaded.description()).isEqualTo("d");
        assertThat(loaded.updatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void savingWithOutdatedVersionThrowsStaleVersion() {
        Card v0 = newCard("A", "V");
        adapter.save(v0.update("B", null, NOW));

        assertThatThrownBy(() -> adapter.save(v0.update("C", null, NOW))).isInstanceOf(StaleVersionException.class);
    }

    @Test
    void lastPositionUsesBinaryCollationOrder() {
        assertThat(adapter.findLastPosition(listId)).isEmpty();
        newCard("a", "V");
        newCard("b", "a");
        newCard("c", "B");

        // "a" (0x61) > "V" (0x56) > "B" (0x42) en orden binario, no en el orden de un locale.
        assertThat(adapter.findLastPosition(listId)).contains(FractionalIndex.of("a"));
    }

    @Test
    void findPositionInListOnlyMatchesTheOwningList() {
        Card c = newCard("A", "V");
        assertThat(adapter.findPositionInList(listId, c.id())).contains(FractionalIndex.of("V"));
        assertThat(adapter.findPositionInList(otherListId, c.id())).isEmpty();
        assertThat(adapter.findPositionInList(listId, UUID.randomUUID())).isEmpty();
    }

    @Test
    void deleteIsHard() {
        Card c = newCard("A", "V");
        adapter.deleteById(c.id());

        assertThat(adapter.findById(c.id())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from card where id = ?", Integer.class, c.id())).isZero();
    }
}
