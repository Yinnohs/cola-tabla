package com.kanbanlive.workspaces.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceView(UUID id, String name, long version, Instant createdAt) {
}
