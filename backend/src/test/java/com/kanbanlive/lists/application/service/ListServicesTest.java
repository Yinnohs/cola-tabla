package com.kanbanlive.lists.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.lists.application.port.in.CreateListUseCase.CreateListCommand;
import com.kanbanlive.lists.application.port.in.ListSummary;
import com.kanbanlive.lists.application.port.in.MoveListUseCase.MoveListCommand;
import com.kanbanlive.lists.application.port.in.RenameListUseCase.RenameListCommand;
import com.kanbanlive.lists.application.port.out.BoardDirectory;
import com.kanbanlive.lists.application.port.out.ListRepository;
import com.kanbanlive.lists.domain.model.BoardList;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.sharedkernel.position.FractionalIndex;
import com.kanbanlive.sharedkernel.position.PositionAssignment;
import com.kanbanlive.testsupport.FixedClock;
import com.kanbanlive.testsupport.SequentialIdGenerator;
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

class ListServicesTest {

    /** Fake en memoria; simula el version de JPA incrementandolo al guardar un existente. */
    static final class InMemoryListRepository implements ListRepository {
        final Map<UUID, BoardList> store = new HashMap<>();

        @Override
        public BoardList save(BoardList l) {
            BoardList saved = store.containsKey(l.id())
                    ? BoardList.rehydrate(l.id(), l.boardId(), l.name(), l.position(), l.version() + 1,
                            l.createdAt(), l.updatedAt())
                    : l;
            store.put(l.id(), saved);
            return saved;
        }

        @Override
        public Optional<BoardList> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsById(UUID id) {
            return store.containsKey(id);
        }

        @Override
        public Optional<FractionalIndex> findLastPosition(UUID boardId) {
            return store.values().stream().filter(l -> l.boardId().equals(boardId)).map(BoardList::position)
                    .max(Comparator.naturalOrder());
        }

        @Override
        public Optional<FractionalIndex> findPositionInBoard(UUID boardId, UUID listId) {
            return Optional.ofNullable(store.get(listId)).filter(l -> l.boardId().equals(boardId))
                    .map(BoardList::position);
        }

        
        public List<UUID> findIdsInOrder(UUID boardId) {
            return store.values().stream().filter(l -> l.boardId().equals(boardId))
                    .sorted(Comparator.comparing(BoardList::position)).map(BoardList::id).toList();
        }

        
        public void updatePositions(List<PositionAssignment> assignments, Instant now) {
            for (PositionAssignment a : assignments) {
                BoardList l = store.get(a.id());
                store.put(a.id(), BoardList.rehydrate(l.id(), l.boardId(), l.name(), a.position(), l.version() + 1,
                        l.createdAt(), now));
            }
        }

        List<String> namesInOrder(UUID boardId) {
            return store.values().stream().filter(l -> l.boardId().equals(boardId))
                    .sorted(Comparator.comparing(BoardList::position)).map(BoardList::name).toList();
        }
    }

    final InMemoryListRepository repo = new InMemoryListRepository();
    final Set<UUID> knownBoards = new HashSet<>();
    final BoardDirectory directory = knownBoards::contains;
    final FixedClock clock = new FixedClock(Instant.parse("2026-01-01T00:00:00Z"));
    final CreateListService create = new CreateListService(repo, directory, new SequentialIdGenerator(), clock);
    final RenameListService rename = new RenameListService(repo, clock);
    final MoveListService move = new MoveListService(repo, clock);
    final UUID board = UUID.randomUUID();

    ListServicesTest() {
        knownBoards.add(board);
    }

    ListSummary newList(String name) {
        return create.execute(new CreateListCommand(board, name));
    }

    @Test
    void createAppendsAtTheEnd() {
        ListSummary a = newList("A");
        ListSummary b = newList("B");
        ListSummary c = newList("C");
        assertThat(a.position()).isLessThan(b.position());
        assertThat(b.position()).isLessThan(c.position());
        assertThat(repo.namesInOrder(board)).containsExactly("A", "B", "C");
        assertThat(a.version()).isZero();
    }

    @Test
    void createInMissingBoardIsNotFound() {
        assertThatThrownBy(() -> create.execute(new CreateListCommand(UUID.randomUUID(), "x")))
                .isInstanceOf(NotFoundException.class);
        assertThat(repo.store).isEmpty();
    }

    @Test
    void createValidatesName() {
        assertThatThrownBy(() -> newList(" ")).isInstanceOf(ValidationException.class);
    }

    @Test
    void renameWithCurrentVersionBumpsVersion() {
        ListSummary a = newList("A");
        ListSummary renamed = rename.execute(new RenameListCommand(a.id(), "AA", a.version()));
        assertThat(renamed.name()).isEqualTo("AA");
        assertThat(renamed.version()).isEqualTo(a.version() + 1);
        assertThat(renamed.position()).isEqualTo(a.position());
    }

    @Test
    void renameWithStaleVersionConflicts() {
        ListSummary a = newList("A");
        assertThatThrownBy(() -> rename.execute(new RenameListCommand(a.id(), "AA", a.version() + 3)))
                .isInstanceOf(StaleVersionException.class);
        assertThat(repo.store.get(a.id()).name()).isEqualTo("A");
    }

    @Test
    void renameMissingListIsNotFound() {
        assertThatThrownBy(() -> rename.execute(new RenameListCommand(UUID.randomUUID(), "x", 0)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void moveBetweenNeighboursReordersChangingOnlyTheMovedList() {
        ListSummary a = newList("A");
        ListSummary b = newList("B");
        ListSummary c = newList("C");

        ListSummary moved = move.execute(new MoveListCommand(c.id(), a.id(), b.id()));

        assertThat(repo.namesInOrder(board)).containsExactly("A", "C", "B");
        assertThat(moved.version()).isEqualTo(c.version() + 1);
        assertThat(repo.store.get(a.id()).position().value()).isEqualTo(a.position());
        assertThat(repo.store.get(b.id()).position().value()).isEqualTo(b.position());
        assertThat(repo.store.get(a.id()).version()).isEqualTo(a.version());
    }

    @Test
    void moveToStartAndEnd() {
        ListSummary a = newList("A");
        ListSummary b = newList("B");
        ListSummary c = newList("C");

        move.execute(new MoveListCommand(c.id(), null, a.id()));
        assertThat(repo.namesInOrder(board)).containsExactly("C", "A", "B");

        move.execute(new MoveListCommand(c.id(), b.id(), null));
        assertThat(repo.namesInOrder(board)).containsExactly("A", "B", "C");
    }

    @Test
    void moveRejectsSelfAsNeighbour() {
        ListSummary a = newList("A");
        assertThatThrownBy(() -> move.execute(new MoveListCommand(a.id(), a.id(), null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void moveRejectsNeighbourFromAnotherBoardOrMissing() {
        ListSummary a = newList("A");
        UUID otherBoard = UUID.randomUUID();
        knownBoards.add(otherBoard);
        ListSummary foreign = create.execute(new CreateListCommand(otherBoard, "F"));

        assertThatThrownBy(() -> move.execute(new MoveListCommand(a.id(), foreign.id(), null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> move.execute(new MoveListCommand(a.id(), null, UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void moveWithInvertedNeighboursIsValidationError() {
        ListSummary a = newList("A");
        ListSummary b = newList("B");
        ListSummary c = newList("C");
        assertThatThrownBy(() -> move.execute(new MoveListCommand(c.id(), b.id(), a.id())))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void moveMissingListIsNotFound() {
        assertThatThrownBy(() -> move.execute(new MoveListCommand(UUID.randomUUID(), null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createRebalancesSiblingsWhenPositionSpaceIsExhaustedAndRetries() {
        UUID id = UUID.randomUUID();
        repo.store.put(id, BoardList.rehydrate(id, board, "Tail", FractionalIndex.of("z".repeat(32)), 0, clock.now(),
                clock.now()));
        UUID id2 = UUID.randomUUID();
        repo.store.put(id2, BoardList.rehydrate(id2, board, "Head", FractionalIndex.of("1"), 0, clock.now(),
                clock.now()));

        ListSummary created = newList("next");

        assertThat(repo.namesInOrder(board)).containsExactly("Head", "Tail", "next");
        assertThat(repo.store.values()).allMatch(l -> !l.position().isExhausted());
        assertThat(repo.store.get(id).version()).isEqualTo(1);
        assertThat(repo.store.get(id2).version()).isEqualTo(1);
        assertThat(created.version()).isZero();
    }

    @Test
    void moveRebalancesSiblingsWhenPositionSpaceIsExhaustedAndRetries() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        // a y b son vecinas con claves tan largas que between(a, b) ya esta agotado.
        String stem = "1".repeat(31);
        repo.store.put(a, BoardList.rehydrate(a, board, "A", FractionalIndex.of(stem + "1"), 0, clock.now(), clock.now()));
        repo.store.put(b, BoardList.rehydrate(b, board, "B", FractionalIndex.of(stem + "2"), 0, clock.now(), clock.now()));
        repo.store.put(c, BoardList.rehydrate(c, board, "C", FractionalIndex.of("z"), 0, clock.now(), clock.now()));

        ListSummary moved = move.execute(new MoveListCommand(c, a, b));

        assertThat(repo.namesInOrder(board)).containsExactly("A", "C", "B");
        assertThat(repo.store.values()).allMatch(l -> !l.position().isExhausted());
        // c se reescribio en el rebalanceo (+1) y luego se movio (+1)
        assertThat(moved.version()).isEqualTo(2);
        assertThat(repo.store.get(a).version()).isEqualTo(1);
        assertThat(repo.store.get(b).version()).isEqualTo(1);
    }

    @Test
    void checkListExistsReflectsRepository() {
        ListSummary a = newList("A");
        CheckListExistsService check = new CheckListExistsService(repo);
        assertThat(check.execute(a.id())).isTrue();
        assertThat(check.execute(UUID.randomUUID())).isFalse();
    }
}
