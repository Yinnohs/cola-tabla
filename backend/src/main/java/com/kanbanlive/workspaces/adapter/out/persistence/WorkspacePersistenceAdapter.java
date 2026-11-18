package com.kanbanlive.workspaces.adapter.out.persistence;

import com.kanbanlive.sharedkernel.domain.StaleVersionException;
import com.kanbanlive.workspaces.application.port.out.WorkspaceRepository;
import com.kanbanlive.workspaces.domain.model.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

@Repository
class WorkspacePersistenceAdapter implements WorkspaceRepository {

    private final WorkspaceJpaRepository jpa;
    private final WorkspaceEntityMapper mapper;

    WorkspacePersistenceAdapter(WorkspaceJpaRepository jpa, WorkspaceEntityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Workspace save(Workspace workspace) {
        try {
            WorkspaceJpaEntity entity = jpa.findById(workspace.id()).map(existing -> {
                if (existing.getVersion() != null && existing.getVersion() != workspace.version()) {
                    throw new StaleVersionException("workspace " + workspace.id() + " was modified concurrently");
                }
                mapper.apply(workspace, existing);
                return existing;
            }).orElseGet(() -> mapper.toNewEntity(workspace));
            return mapper.toDomain(jpa.saveAndFlush(entity));
        } catch (OptimisticLockingFailureException ex) {
            throw new StaleVersionException("workspace " + workspace.id() + " was modified concurrently");
        }
    }

    @Override
    public Optional<Workspace> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return jpa.existsById(id);
    }

    @Override
    public List<Workspace> findAll() {
        return jpa.findAllByOrderByCreatedAtAscIdAsc().stream().map(mapper::toDomain).toList();
    }
}
