package com.kanbanlive.workspaces.application.port.in;

import java.time.Instant;
import java.util.UUID;

/** role: rol del usuario actual en el workspace (OWNER, EDITOR o VIEWER). */
public record WorkspaceView(UUID id, String name, long version, Instant createdAt, String role) {
}
