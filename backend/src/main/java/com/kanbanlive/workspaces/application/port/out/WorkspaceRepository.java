package com.kanbanlive.workspaces.application.port.out;

import com.kanbanlive.workspaces.domain.model.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository {

    Workspace save(Workspace workspace);

    Optional<Workspace> findById(UUID id);

    boolean existsById(UUID id);

    /** Ordenados por fecha de creacion y luego id (estable). */
    List<Workspace> findAll();
}
