package com.kanbanlive.workspaces.application.service;

import com.kanbanlive.workspaces.application.port.in.WorkspaceView;
import com.kanbanlive.workspaces.domain.model.Workspace;

final class WorkspaceViews {

    private WorkspaceViews() {
    }

    static WorkspaceView of(Workspace w) {
        return new WorkspaceView(w.id(), w.name(), w.version(), w.createdAt());
    }
}
