package com.kanbanlive.lists.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record ListSummary(UUID id, UUID boardId, String name, String position, long version, Instant createdAt) {
}
