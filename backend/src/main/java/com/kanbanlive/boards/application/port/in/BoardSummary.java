package com.kanbanlive.boards.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record BoardSummary(UUID id, UUID workspaceId, String name, long version, Instant createdAt) {
}
