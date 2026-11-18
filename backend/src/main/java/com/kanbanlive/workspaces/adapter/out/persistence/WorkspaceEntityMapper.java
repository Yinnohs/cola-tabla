package com.kanbanlive.workspaces.adapter.out.persistence;

import com.kanbanlive.workspaces.domain.model.Workspace;
import org.springframework.stereotype.Component;

@Component
class WorkspaceEntityMapper {

    Workspace toDomain(WorkspaceJpaEntity e) {
        long version = e.getVersion() == null ? 0 : e.getVersion();
        return Workspace.rehydrate(e.getId(), e.getName(), version, e.getCreatedAt(), e.getUpdatedAt());
    }

    WorkspaceJpaEntity toNewEntity(Workspace w) {
        return new WorkspaceJpaEntity(w.id(), w.name(), w.createdAt(), w.updatedAt());
    }

    void apply(Workspace w, WorkspaceJpaEntity e) {
        e.setName(w.name());
        e.setUpdatedAt(w.updatedAt());
    }
}
