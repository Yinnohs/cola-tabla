package com.kanbanlive.cards.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.cards.application.port.in.CardSummary;
import com.kanbanlive.cards.application.port.in.CreateCardUseCase.CreateCardCommand;
import com.kanbanlive.cards.application.port.in.DeleteCardUseCase.DeleteCardCommand;
import com.kanbanlive.cards.application.port.in.MoveCardUseCase.MoveCardCommand;
import com.kanbanlive.cards.application.port.in.UpdateCardUseCase.UpdateCardCommand;
import com.kanbanlive.cards.application.port.out.CardRepository;
import com.kanbanlive.cards.application.port.out.ListDirectory;
import com.kanbanlive.cards.domain.model.Card;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import com.kanbanlive.sharedkernel.position.PositionAssignment;
import com.kanbanlive.testsupport.FixedClock;
import com.kanbanlive.testsupport.SequentialIdGenerator;
import com.kanbanlive.testsupport.RecordingBoardChanges;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CardServicesTest {

    /** Fake en memoria; simula el version de JPA incrementandolo al guardar un existente. */
    static final class InMemoryCardRepository implements CardRepository {
        final Map<UUID, Card> store = new HashMap<>();

        @Override
        public Card save(Card c) {
            Card saved = store.containsKey(c.id())
                    ? Card.rehydrate(c.id(), c.listId(), c.title(), c.description(), c.position(), c.version() + 1,
                            c.createdAt(), c.updatedAt())
                    : c;
            store.put(c.id(), saved);
            return saved;
        }

        /** Last-write-wins: ignora la version del argumento y siempre incrementa la almacenada. */
        @Override
        public Card saveMove(Card c) {
            Card current = store.get(c.id());
            Card saved = Card.rehydrate(c.id(), c.listId(), current.title(), current.description(), c.position(),
                    current.version() + 1, current.createdAt(), c.updatedAt());
            store.put(c.id(), saved);
            return saved;
        }

        @Override
        public Optional<Card> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public void deleteById(UUID id) {
            store.remove(id);
        }

        @Override
        public Optional<FractionalIndex> findLastPosition(UUID listId) {
            return store.values().stream().filter(c -> c.listId().equals(listId)).map(Card::position)
                    .max(Comparator.naturalOrder());
        }

        @Override
        public Optional<FractionalIndex> findPositionInList(UUID listId, UUID cardId) {
            return Optional.ofNullable(store.get(cardId)).filter(c -> c.listId().equals(listId))
                    .map(Card::position);
        }

        @Override
        public List<UUID> findIdsInOrder(UUID listId) {
            return store.values().stream().filter(c -> c.listId().equals(listId))
                    .sorted(Comparator.comparing(Card::position)).map(Card::id).toList();
        }

        @Override
        public void updatePositions(List<PositionAssignment> assignments, Instant now) {
            for (PositionAssignment a : assignments) {
                Card c = store.get(a.id());
                store.put(a.id(), Card.rehydrate(c.id(), c.listId(), c.title(), c.description(), a.position(),
                        c.version() + 1, c.createdAt(), now));
            }
        }

        List<String> titlesInOrder(UUID listId) {
            return store.values().stream().filter(c -> c.listId().equals(listId))
                    .sorted(Comparator.comparing(Card::position)).map(Card::title).toList();
        }
    }

    final InMemoryCardRepository repo = new InMemoryCardRepository();
    final Set<UUID> knownLists = new HashSet<>();
    final UUID board = UUID.randomUUID();
    final ListDirectory directory = new ListDirectory() {
        @Override
        public boolean exists(UUID listId) {
            return knownLists.contains(listId);
        }

        @Override
        public Optional<UUID> boardIdOf(UUID listId) {
            return knownLists.contains(listId) ? Optional.of(board) : Optional.empty();
        }
    };
    final RecordingBoardChanges changes = new RecordingBoardChanges();
    final FixedClock clock = new FixedClock(Instant.parse("2026-01-01T00:00:00Z"));
    final CreateCardService create = new CreateCardService(repo, directory, new SequentialIdGenerator(), clock, changes);
    final UpdateCardService update = new UpdateCardService(repo, directory, clock, changes);
    final MoveCardService move = new MoveCardService(repo, directory, clock, changes);
    final DeleteCardService delete = new DeleteCardService(repo, directory, changes);
    final UUID list = UUID.randomUUID();
    final UUID otherList = UUID.randomUUID();

    CardServicesTest() {
        knownLists.add(list);
        knownLists.add(otherList);
    }

    @Test
    void mutationsRecordTheirBoardEvents() {
        CardSummary card = newCard(list, "a");
        update.execute(new UpdateCardCommand(card.id(), "b", null, card.version()));
        move.execute(new MoveCardCommand(card.id(), otherList, null, null));
        delete.execute(new DeleteCardCommand(card.id()));

        assertThat(changes.types()).containsExactly("card.created", "card.updated", "card.moved", "card.deleted");
        assertThat(changes.changes).allMatch(c -> c.boardId().equals(board));
    }

    @Test
    void failedMutationRecordsNothing() {
        assertThatThrownBy(() -> delete.execute(new DeleteCardCommand(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
        assertThat(changes.changes).isEmpty();
    }

    CardSummary newCard(UUID listId, String title) {
        return create.execute(new CreateCardCommand(listId, title, null));
    }

    @Test
    void createAppendsAtTheEndOfTheList() {
        CardSummary a = newCard(list, "A");
        CardSummary b = newCard(list, "B");
        newCard(otherList, "X");
        CardSummary c = create.execute(new CreateCardCommand(list, "C", "desc"));

        assertThat(a.position()).isLessThan(b.position());
        assertThat(b.position()).isLessThan(c.position());
        assertThat(c.description()).isEqualTo("desc");
        assertThat(repo.titlesInOrder(list)).containsExactly("A", "B", "C");
        assertThat(a.version()).isZero();
    }

    @Test
    void createInMissingListIsNotFound() {
        assertThatThrownBy(() -> newCard(UUID.randomUUID(), "x")).isInstanceOf(NotFoundException.class);
        assertThat(repo.store).isEmpty();
    }

    @Test
    void createValidatesTitle() {
        assertThatThrownBy(() -> newCard(list, " ")).isInstanceOf(ValidationException.class);
    }

    @Test
    void updateReplacesFieldsAndBumpsVersion() {
        CardSummary a = newCard(list, "A");
        CardSummary updated = update.execute(new UpdateCardCommand(a.id(), "AA", "new desc", a.version()));
        assertThat(updated.title()).isEqualTo("AA");
        assertThat(updated.description()).isEqualTo("new desc");
        assertThat(updated.version()).isEqualTo(a.version() + 1);
        assertThat(updated.position()).isEqualTo(a.position());
    }

    @Test
    void updateWithStaleVersionConflicts() {
        CardSummary a = newCard(list, "A");
        assertThatThrownBy(() -> update.execute(new UpdateCardCommand(a.id(), "AA", null, a.version() + 1)))
                .isInstanceOfSatisfying(StaleVersionException.class, ex -> {
                    // B4.4: el 409 transporta el estado actual para que el cliente compare.
                    assertThat(ex.current()).isInstanceOf(CardSummary.class);
                    assertThat(((CardSummary) ex.current()).title()).isEqualTo("A");
                    assertThat(((CardSummary) ex.current()).version()).isEqualTo(a.version());
                });
        assertThat(repo.store.get(a.id()).title()).isEqualTo("A");
    }

    @Test
    void updateMissingCardIsNotFound() {
        assertThatThrownBy(() -> update.execute(new UpdateCardCommand(UUID.randomUUID(), "x", null, 0)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void moveWithinListBetweenNeighbours() {
        CardSummary a = newCard(list, "A");
        CardSummary b = newCard(list, "B");
        CardSummary c = newCard(list, "C");

        CardSummary moved = move.execute(new MoveCardCommand(c.id(), list, a.id(), b.id()));

        assertThat(repo.titlesInOrder(list)).containsExactly("A", "C", "B");
        assertThat(moved.version()).isEqualTo(c.version() + 1);
        assertThat(repo.store.get(a.id()).position().value()).isEqualTo(a.position());
        assertThat(repo.store.get(b.id()).version()).isEqualTo(b.version());
    }

    @Test
    void moveToAnotherListBetweenItsCards() {
        CardSummary a = newCard(list, "A");
        CardSummary x = newCard(otherList, "X");
        CardSummary y = newCard(otherList, "Y");

        CardSummary moved = move.execute(new MoveCardCommand(a.id(), otherList, x.id(), y.id()));

        assertThat(moved.listId()).isEqualTo(otherList);
        assertThat(repo.titlesInOrder(otherList)).containsExactly("X", "A", "Y");
        assertThat(repo.titlesInOrder(list)).isEmpty();
    }

    @Test
    void moveToEmptyListWithoutNeighbours() {
        CardSummary a = newCard(list, "A");
        UUID empty = UUID.randomUUID();
        knownLists.add(empty);

        CardSummary moved = move.execute(new MoveCardCommand(a.id(), empty, null, null));

        assertThat(moved.listId()).isEqualTo(empty);
    }

    @Test
    void moveToMissingListIsNotFound() {
        CardSummary a = newCard(list, "A");
        assertThatThrownBy(() -> move.execute(new MoveCardCommand(a.id(), UUID.randomUUID(), null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void moveRejectsNeighbourOutsideTargetListOrSelf() {
        CardSummary a = newCard(list, "A");
        CardSummary b = newCard(list, "B");
        CardSummary x = newCard(otherList, "X");

        assertThatThrownBy(() -> move.execute(new MoveCardCommand(a.id(), otherList, b.id(), null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> move.execute(new MoveCardCommand(a.id(), list, null, UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> move.execute(new MoveCardCommand(a.id(), otherList, a.id(), x.id())))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void moveWithInvertedNeighboursIsValidationError() {
        CardSummary a = newCard(list, "A");
        CardSummary b = newCard(list, "B");
        CardSummary c = newCard(list, "C");
        assertThatThrownBy(() -> move.execute(new MoveCardCommand(c.id(), list, b.id(), a.id())))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void moveMissingCardIsNotFound() {
        assertThatThrownBy(() -> move.execute(new MoveCardCommand(UUID.randomUUID(), list, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createRebalancesSiblingsWhenPositionSpaceIsExhaustedAndRetries() {
        UUID id = UUID.randomUUID();
        repo.store.put(id, Card.rehydrate(id, otherList, "Tail", null, FractionalIndex.of("z".repeat(32)), 0,
                clock.now(), clock.now()));

        CardSummary created = newCard(otherList, "next");

        assertThat(repo.titlesInOrder(otherList)).containsExactly("Tail", "next");
        assertThat(repo.store.values()).allMatch(c -> !c.position().isExhausted());
        assertThat(repo.store.get(id).version()).isEqualTo(1);
        assertThat(created.version()).isZero();
    }

    @Test
    void moveRebalancesTargetListWhenPositionSpaceIsExhaustedAndRetries() {
        CardSummary moving = newCard(list, "M");
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String stem = "1".repeat(31);
        repo.store.put(a, Card.rehydrate(a, otherList, "A", null, FractionalIndex.of(stem + "1"), 0, clock.now(),
                clock.now()));
        repo.store.put(b, Card.rehydrate(b, otherList, "B", null, FractionalIndex.of(stem + "2"), 0, clock.now(),
                clock.now()));

        CardSummary moved = move.execute(new MoveCardCommand(moving.id(), otherList, a, b));

        assertThat(repo.titlesInOrder(otherList)).containsExactly("A", "M", "B");
        assertThat(repo.store.values()).allMatch(c -> !c.position().isExhausted());
        assertThat(moved.version()).isEqualTo(moving.version() + 1);
        assertThat(repo.store.get(a).version()).isEqualTo(1);
        assertThat(repo.store.get(b).version()).isEqualTo(1);
    }

    @Test
    void moveWithinSameListRebalancesIncludingTheMovingCard() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        String stem = "1".repeat(31);
        repo.store.put(a, Card.rehydrate(a, list, "A", null, FractionalIndex.of(stem + "1"), 0, clock.now(), clock.now()));
        repo.store.put(b, Card.rehydrate(b, list, "B", null, FractionalIndex.of(stem + "2"), 0, clock.now(), clock.now()));
        repo.store.put(c, Card.rehydrate(c, list, "C", null, FractionalIndex.of("z"), 0, clock.now(), clock.now()));

        CardSummary moved = move.execute(new MoveCardCommand(c, list, a, b));

        assertThat(repo.titlesInOrder(list)).containsExactly("A", "C", "B");
        // rebalanceo (+1) y movimiento (+1): se recargo la tarjeta tras rebalancear, sin StaleVersion
        assertThat(moved.version()).isEqualTo(2);
    }

    @Test
    void deleteRemovesTheCardHard() {
        CardSummary a = newCard(list, "A");
        delete.execute(new DeleteCardCommand(a.id()));
        assertThat(repo.store).doesNotContainKey(a.id());
    }

    @Test
    void deleteMissingCardIsNotFound() {
        assertThatThrownBy(() -> delete.execute(new DeleteCardCommand(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
    }
}
