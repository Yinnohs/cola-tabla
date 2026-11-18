package com.kanbanlive.workspaces.adapter.in.web;

import com.kanbanlive.workspaces.application.port.in.WorkspaceView;
import java.time.Instant;
import java.util.UUID;

record WorkspaceResponse(UUID id, String name, long version, Instant createdAt) {

    static WorkspaceResponse from(WorkspaceView v) {
        return new WorkspaceResponse(v.id(), v.name(), v.version(), v.createdAt());
    }
}
