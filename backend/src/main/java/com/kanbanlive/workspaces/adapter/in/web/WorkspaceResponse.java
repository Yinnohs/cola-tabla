package com.kanbanlive.workspaces.adapter.in.web;

import com.kanbanlive.workspaces.application.port.in.WorkspaceView;
import java.time.Instant;
import java.util.UUID;

/** role: rol del usuario actual en el workspace (OWNER, EDITOR o VIEWER). */
record WorkspaceResponse(UUID id, String name, long version, Instant createdAt, String role) {

    static WorkspaceResponse from(WorkspaceView v) {
        return new WorkspaceResponse(v.id(), v.name(), v.version(), v.createdAt(), v.role());
    }
}
