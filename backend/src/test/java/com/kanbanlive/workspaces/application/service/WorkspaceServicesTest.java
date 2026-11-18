package com.kanbanlive.workspaces.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.sharedkernel.domain.NotFoundException;
import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.testsupport.FixedClock;
import com.kanbanlive.testsupport.SequentialIdGenerator;
import com.kanbanlive.workspaces.application.port.in.CreateWorkspaceUseCase.CreateWorkspaceCommand;
import com.kanbanlive.workspaces.application.port.in.WorkspaceView;
import com.kanbanlive.workspaces.application.port.out.WorkspaceRepository;
import com.kanbanlive.workspaces.domain.model.Workspace;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceServicesTest {

    /** Fake en memoria del puerto de salida. */
    static final class InMemoryWorkspaceRepository implements WorkspaceRepository {
        final Map<UUID, Workspace> store = new LinkedHashMap<>();

        @Override
        public Workspace save(Workspace w) {
            store.put(w.id(), w);
            return w;
        }

        @Override
        public Optional<Workspace> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean existsById(UUID id) {
            return store.containsKey(id);
        }

        @Override
        public List<Workspace> findAll() {
            return new ArrayList<>(store.values());
        }
    }

    final InMemoryWorkspaceRepository repo = new InMemoryWorkspaceRepository();
    final FixedClock clock = new FixedClock(Instant.parse("2026-01-01T00:00:00Z"));
    final List<String> owners = new ArrayList<>();
    final CreateWorkspaceService create = new CreateWorkspaceService(repo, new SequentialIdGenerator(), clock,
            () -> "user-1", (workspaceId, userId) -> owners.add(workspaceId + ":" + userId));
    final ListWorkspacesService list = new ListWorkspacesService(repo, () -> "user-1", userId -> java.util.Set.of());
    final ListWorkspacesService listAll = new ListWorkspacesService(repo, new com.kanbanlive.sharedkernel.application.CurrentUser() {
        @Override
        public String id() {
            return "dev";
        }

        @Override
        public boolean unrestricted() {
            return true;
        }
    }, userId -> java.util.Set.of());
    final CheckWorkspaceExistsService exists = new CheckWorkspaceExistsService(repo);

    @Test
    void createPersistsAndReturnsView() {
        WorkspaceView view = create.execute(new CreateWorkspaceCommand("Acme"));
        assertThat(view.id()).isEqualTo(SequentialIdGenerator.id(1));
        assertThat(view.name()).isEqualTo("Acme");
        assertThat(view.createdAt()).isEqualTo(clock.now());
        assertThat(repo.store).containsKey(view.id());
    }

    @Test
    void creatorBecomesOwner() {
        WorkspaceView view = create.execute(new CreateWorkspaceCommand("Acme"));
        assertThat(owners).containsExactly(view.id() + ":user-1");
    }

    @Test
    void createRejectsInvalidName() {
        assertThatThrownBy(() -> create.execute(new CreateWorkspaceCommand(" ")))
                .isInstanceOf(ValidationException.class);
        assertThat(repo.store).isEmpty();
        assertThat(owners).isEmpty();
    }

    @Test
    void listReturnsAll() {
        create.execute(new CreateWorkspaceCommand("A"));
        create.execute(new CreateWorkspaceCommand("B"));
        assertThat(listAll.execute()).extracting(WorkspaceView::name).containsExactly("A", "B");
        assertThat(list.execute()).isEmpty();
    }

    @Test
    void existsReflectsRepository() {
        WorkspaceView view = create.execute(new CreateWorkspaceCommand("A"));
        assertThat(exists.execute(view.id())).isTrue();
        assertThat(exists.execute(UUID.randomUUID())).isFalse();
    }

    @Test
    void getReturnsViewOrNotFound() {
        WorkspaceView view = create.execute(new CreateWorkspaceCommand("Acme"));
        GetWorkspaceService get = new GetWorkspaceService(repo);
        assertThat(get.execute(view.id())).isEqualTo(view);
        assertThatThrownBy(() -> get.execute(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
