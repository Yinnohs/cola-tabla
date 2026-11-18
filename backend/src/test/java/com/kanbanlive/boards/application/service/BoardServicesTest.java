package com.kanbanlive.boards.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.boards.application.port.in.BoardDetails;
import com.kanbanlive.boards.application.port.in.BoardSummary;
import com.kanbanlive.boards.application.port.in.CreateBoardUseCase.CreateBoardCommand;
import com.kanbanlive.boards.application.port.in.RenameBoardUseCase.RenameBoardCommand;
import com.kanbanlive.boards.application.port.out.BoardRepository;
import com.kanbanlive.boards.application.port.out.WorkspaceDirectory;
import com.kanbanlive.boards.domain.model.Board;
import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.testsupport.FixedClock;
import com.kanbanlive.testsupport.SequentialIdGenerator;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardServicesTest {

    /** Fake en memoria; simula el @Version de JPA incrementando la version al guardar. */
    static final class InMemoryBoardRepository implements BoardRepository {
        final Map<UUID, Board> store = new HashMap<>();

        @Override
        public Board save(Board b) {
            Board saved = store.containsKey(b.id())
                    ? Board.rehydrate(b.id(), b.workspaceId(), b.name(), b.version() + 1, b.createdAt(), b.updatedAt())
                    : b;
            store.put(b.id(), saved);
            return saved;
        }

        @Override
        public Optional<Board> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsById(UUID id) {
            return store.containsKey(id);
        }

        @Override
        public List<Board> findByWorkspaceId(UUID workspaceId) {
            return store.values().stream().filter(b -> b.workspaceId().equals(workspaceId)).toList();
        }
    }

    final InMemoryBoardRepository repo = new InMemoryBoardRepository();
    final Set<UUID> knownWorkspaces = new HashSet<>();
    final WorkspaceDirectory directory = knownWorkspaces::contains;
    final FixedClock clock = new FixedClock(Instant.parse("2026-01-01T00:00:00Z"));
    final CreateBoardService create = new CreateBoardService(repo, directory, new SequentialIdGenerator(), clock);
    final RenameBoardService rename = new RenameBoardService(repo, clock);
    final UUID ws = UUID.randomUUID();

    BoardServicesTest() {
        knownWorkspaces.add(ws);
    }

    @Test
    void createBoardInExistingWorkspace() {
        BoardSummary s = create.execute(new CreateBoardCommand(ws, "Sprint"));
        assertThat(s.workspaceId()).isEqualTo(ws);
        assertThat(s.name()).isEqualTo("Sprint");
        assertThat(repo.store).containsKey(s.id());
    }

    @Test
    void createBoardInMissingWorkspaceIsNotFound() {
        assertThatThrownBy(() -> create.execute(new CreateBoardCommand(UUID.randomUUID(), "x")))
                .isInstanceOf(NotFoundException.class);
        assertThat(repo.store).isEmpty();
    }

    @Test
    void createBoardValidatesName() {
        assertThatThrownBy(() -> create.execute(new CreateBoardCommand(ws, " ")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void renameWithCurrentVersionSucceedsAndBumpsVersion() {
        BoardSummary created = create.execute(new CreateBoardCommand(ws, "Old"));
        clock.set(Instant.parse("2026-02-01T00:00:00Z"));
        BoardSummary renamed = rename.execute(new RenameBoardCommand(created.id(), "New", created.version()));
        assertThat(renamed.name()).isEqualTo("New");
        assertThat(renamed.version()).isEqualTo(created.version() + 1);
        assertThat(repo.store.get(created.id()).updatedAt()).isEqualTo(clock.now());
    }

    @Test
    void renameWithStaleVersionConflicts() {
        BoardSummary created = create.execute(new CreateBoardCommand(ws, "Old"));
        assertThatThrownBy(() -> rename.execute(new RenameBoardCommand(created.id(), "New", created.version() + 5)))
                .isInstanceOf(StaleVersionException.class);
        assertThat(repo.store.get(created.id()).name()).isEqualTo("Old");
    }

    @Test
    void renameMissingBoardIsNotFound() {
        assertThatThrownBy(() -> rename.execute(new RenameBoardCommand(UUID.randomUUID(), "x", 0)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void checkBoardExistsReflectsRepository() {
        BoardSummary created = create.execute(new CreateBoardCommand(ws, "B"));
        CheckBoardExistsService check = new CheckBoardExistsService(repo);
        assertThat(check.execute(created.id())).isTrue();
        assertThat(check.execute(UUID.randomUUID())).isFalse();
    }

    @Test
    void getBoardReturnsReaderResultOrNotFound() {
        UUID id = UUID.randomUUID();
        BoardDetails details = new BoardDetails(id, ws, "B", 0, List.of());
        GetBoardService get = new GetBoardService(boardId -> boardId.equals(id) ? Optional.of(details) : Optional.empty());
        assertThat(get.execute(id)).isSameAs(details);
        assertThatThrownBy(() -> get.execute(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listBoardsReturnsOnlyBoardsOfWorkspace() {
        UUID other = UUID.randomUUID();
        knownWorkspaces.add(other);
        create.execute(new CreateBoardCommand(ws, "A"));
        create.execute(new CreateBoardCommand(other, "Other"));
        create.execute(new CreateBoardCommand(ws, "B"));
        ListBoardsService list = new ListBoardsService(repo, directory);

        assertThat(list.execute(ws)).extracting(BoardSummary::name).containsExactlyInAnyOrder("A", "B");
        assertThat(list.execute(ws)).allMatch(b -> b.workspaceId().equals(ws));
    }

    @Test
    void listBoardsOfEmptyWorkspaceIsEmptyAndOfMissingWorkspaceIsNotFound() {
        ListBoardsService list = new ListBoardsService(repo, directory);
        assertThat(list.execute(ws)).isEmpty();
        assertThatThrownBy(() -> list.execute(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
